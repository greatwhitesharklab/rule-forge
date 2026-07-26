"""MemoryInjection tests (P2 task 2, design doc §1.2 / §5).

Hard acceptance criteria covered here:
  * zero-init bit identity: with mem_out zero-initialized, the welded model's
    logits are bitwise identical to baseline even when the reader returns
    non-trivial memory hits (proving the identity comes from the zero
    initialization, not from an empty retrieval);
  * pathway liveness: non-zero mem_out weights provably change the output.

Everything runs on the tiny random-weight Qwen3 (hidden 64) with a fake
MemoryReader — no network, sub-second.
"""

from __future__ import annotations

import numpy as np
import pytest
import torch

from _tiny import make_tiny_qwen3
from llm import MemoryHit, MemoryInjection

HIDDEN = 64  # tiny model hidden_size
QUERY_DIM = 16  # test-side query projection dim (production: 256)


class FakeReader:
    """Deterministic MemoryReader stand-in; records every query it gets."""

    def __init__(self, n_hits: int = 3, seed: int = 0):
        rng = np.random.default_rng(seed)
        self.hits = [
            MemoryHit(
                value_vec=rng.standard_normal(HIDDEN).astype(np.float32),
                weight=w,
                key_vec=rng.standard_normal(QUERY_DIM).astype(np.float32),
            )
            for w in (0.9, 0.6, 0.3)[:n_hits]
        ]
        self.calls: list[np.ndarray] = []

    def read(self, query: np.ndarray, k: int) -> list[MemoryHit]:
        self.calls.append(query)
        assert query.shape == (QUERY_DIM,)
        return self.hits[:k]


def _ids() -> torch.Tensor:
    return torch.tensor([[10, 20, 30, 40], [11, 21, 31, 41]], dtype=torch.long)


def _logits(model: torch.nn.Module, ids: torch.Tensor) -> torch.Tensor:
    with torch.no_grad():
        return model(ids).logits


@pytest.fixture()
def model() -> torch.nn.Module:
    return make_tiny_qwen3(seed=0, layers=3)


@pytest.fixture()
def injection(model):
    inj = MemoryInjection(model, FakeReader(), layer_idx=2, query_dim=QUERY_DIM)
    yield inj
    inj.detach()


# ---------------------------------------------------------------- zero init


def test_zero_init_bit_identical_with_nontrivial_hits(model, injection) -> None:
    """P2 acceptance: welded output == baseline, bitwise, at zero init."""
    ids = _ids()
    baseline = _logits(model, ids)
    welded = _logits(model, ids)
    diff = (welded - baseline).abs().max().item()
    assert diff < 1e-7, f"max|delta| = {diff}"
    # The reader really delivered memory — identity comes from zero-init
    # mem_out, not from an empty retrieval.
    assert len(injection.last_hits) == ids.shape[0]
    assert all(h > 0 for h in injection.last_hits)
    assert all(g > 0.0 for g in injection.last_gates)


def test_pathway_alive_when_mem_out_nonzero(model, injection) -> None:
    """Fill mem_out with non-zero weights: output MUST change (path is live)."""
    ids = _ids()
    baseline = _logits(model, ids)
    with torch.no_grad():
        injection.mem_out.weight.normal_(0, 0.1)
        injection.mem_out.bias.normal_(0, 0.1)
    welded = _logits(model, ids)
    assert (welded - baseline).abs().max().item() > 1e-4


def test_memory_miss_injects_exactly_zero(model) -> None:
    ids = _ids()
    baseline = _logits(model, ids)
    with MemoryInjection(
        model, FakeReader(n_hits=0), layer_idx=2, query_dim=QUERY_DIM
    ) as inj:
        with torch.no_grad():
            inj.mem_out.weight.normal_(0, 0.1)  # even live weights inject nothing
        welded = _logits(model, ids)
        assert inj.last_hits == [0, 0]
        assert inj.last_gates == [0.0, 0.0]
    assert torch.equal(welded, baseline)


# ------------------------------------------------------------------- pieces


def test_gate_range(model, injection) -> None:
    _logits(model, _ids())
    assert injection.last_gates
    assert all(0.0 <= g <= 1.0 for g in injection.last_gates)


def test_query_proj_frozen(model, injection) -> None:
    assert all(not p.requires_grad for p in injection.query_proj.parameters())
    # And therefore absent from any optimizer built over trainable params.
    trainable = {id(p) for p in injection.parameters() if p.requires_grad}
    assert all(
        id(p) not in trainable for p in injection.query_proj.parameters()
    )
    # mem_out and gate stay trainable (they are the P2/P3 learning surface).
    assert all(p.requires_grad for p in injection.mem_out.parameters())
    assert all(p.requires_grad for p in injection.gate.parameters())


def test_detach_restores_model(model, injection) -> None:
    ids = _ids()
    baseline = _logits(model, ids)
    with torch.no_grad():
        injection.mem_out.weight.normal_(0, 0.1)
    injection.detach()
    layer = model.model.layers[2]
    assert not layer._forward_hooks
    assert torch.equal(_logits(model, ids), baseline)


def test_context_manager_detaches(model) -> None:
    layer = model.model.layers[2]
    with MemoryInjection(model, FakeReader(), layer_idx=2, query_dim=QUERY_DIM):
        assert layer._forward_hooks
    assert not layer._forward_hooks


# ------------------------------------------------------------------ shapes


def test_batch_shape_and_per_sample_retrieval(model) -> None:
    reader = FakeReader()
    ids = torch.randint(8, 1000, (3, 5), dtype=torch.long)
    with MemoryInjection(model, reader, layer_idx=2, query_dim=QUERY_DIM) as inj:
        with torch.no_grad():
            inj.mem_out.weight.normal_(0, 0.1)
        out = model(ids).logits
    assert out.shape == (3, 5, 1000)
    assert len(reader.calls) == 3  # one retrieval per batch sample


def test_mean_pooling_variant(model) -> None:
    ids = _ids()
    baseline = _logits(model, ids)
    with MemoryInjection(
        model, FakeReader(), layer_idx=2, query_dim=QUERY_DIM, pooling="mean"
    ) as inj:
        welded = _logits(model, ids)
        assert inj.last_hits == [3, 3]  # pooling variant still retrieves per sample
    assert torch.equal(welded, baseline)


def test_invalid_layer_idx_raises(model) -> None:
    with pytest.raises(IndexError):
        MemoryInjection(model, FakeReader(), layer_idx=99, query_dim=QUERY_DIM)
