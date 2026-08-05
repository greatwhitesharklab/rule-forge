"""Tests for wired per-layer composition (ParameterComposer.wire).

Core acceptance: the gradient probe — after wiring, a backward pass must
deliver non-zero gradient to ALL 84 gates (28 layers x 3 PM columns),
fixing the "83 gates with identically zero gradient" flaw of the
logit-level composer path.
"""

from __future__ import annotations

import torch
import torch.nn as nn

from selflearn.composer import (
    CompositionConfig,
    ParameterComposer,
    PhiType,
    compute_weight_deltas,
)


class TinyBase(nn.Module):
    """Minimal base model with per-layer q_proj/v_proj linears."""

    def __init__(self, dim: int = 8, vocab: int = 20, n_layers: int = 28):
        super().__init__()
        self.embed = nn.Embedding(vocab, dim)
        self.layers = nn.ModuleList([
            nn.ModuleDict({
                "q_proj": nn.Linear(dim, dim),
                "v_proj": nn.Linear(dim, dim),
            }) for _ in range(n_layers)
        ])
        self.lm_head = nn.Linear(dim, vocab)
        self.config = type("C", (), {"hidden_size": dim,
                                     "num_hidden_layers": n_layers})()

    def get_input_embeddings(self):
        return self.embed

    def get_output_embeddings(self):
        return self.lm_head

    def forward(self, input_ids, attention_mask=None):
        h = self.embed(input_ids)
        for layer in self.layers:
            h = layer["q_proj"](h)
            h = layer["v_proj"](h)
        return type("O", (), {"logits": self.lm_head(h)})()


def _dense_deltas(base: TinyBase, value: float = 0.05) -> dict[str, torch.Tensor]:
    """Deterministic non-zero dense deltas for every target module."""
    torch.manual_seed(7)
    out: dict[str, torch.Tensor] = {}
    for name, module in base.named_modules():
        if isinstance(module, nn.Linear) and ("q_proj" in name or "v_proj" in name):
            out[name] = torch.randn_like(module.weight) * value
    return out


def _wired_composer(n_layers: int = 28, with_user: bool = True):
    torch.manual_seed(0)
    base = TinyBase(n_layers=n_layers)
    c = ParameterComposer(base, CompositionConfig(n_layers=n_layers,
                                                  phi_type=PhiType.GATE_ADD))
    c.init_session_lora(r=4)
    domain = _dense_deltas(base)
    user = _dense_deltas(base) if with_user else None
    n_hooked = c.wire(domain_deltas=domain, user_deltas=user)
    return base, c, n_hooked


class TestWireBasics:
    def test_hooks_all_target_modules(self) -> None:
        _, _, n_hooked = _wired_composer()
        assert n_hooked == 56  # 28 layers x (q_proj + v_proj)

    def test_lossless_at_zero_init(self) -> None:
        """Session B = 0 and no dense deltas: wiring is bit-identical."""
        torch.manual_seed(0)
        base = TinyBase()
        c = ParameterComposer(base, CompositionConfig(n_layers=28))
        c.init_session_lora(r=4)
        x = torch.randint(0, 20, (2, 5))
        with torch.no_grad():
            before = base(x).logits.clone()
        c.wire()  # no domain/user deltas, session LoRA B = 0
        with torch.no_grad():
            after = base(x).logits
        assert torch.equal(before, after)

    def test_gate_modulates_output(self) -> None:
        base, c, _ = _wired_composer()
        x = torch.randint(0, 20, (2, 5))
        with torch.no_grad():
            ref = base(x).logits.clone()
            c.gates.data[:, 0] = -10.0  # domain gate ~ 0
            off = base(x).logits.clone()
            c.gates.data[:, 0] = 10.0   # domain gate ~ 1
            on = base(x).logits.clone()
        assert not torch.allclose(ref, on)
        assert torch.allclose(off, on, atol=1e-3) is False
        # off (domain ~0, session B=0, user still 0.5) differs from ref less
        # than on does: gate actually scales the delta.
        d_off = (off - ref).abs().sum()
        d_on = (on - ref).abs().sum()
        assert d_off < d_on

    def test_unwire_restores(self) -> None:
        base, c, _ = _wired_composer()
        x = torch.randint(0, 20, (2, 5))
        c.unwire()
        torch.manual_seed(0)
        base2 = TinyBase()
        with torch.no_grad():
            a = base(x).logits
            b = base2(x).logits
        assert torch.allclose(a, b)


class TestGradientProbe:
    """Core acceptance: every one of the 84 gates receives gradient."""

    def test_all_84_gates_nonzero_grad(self) -> None:
        base, c, _ = _wired_composer(n_layers=28)
        opt = torch.optim.AdamW(c.get_trainable_parameters(), lr=1e-3)
        x = torch.randint(0, 20, (2, 5))

        # Step 0: domain/user gates already get gradient (dense deltas),
        # session gates do not yet — session delta is identically zero at
        # the B = 0 init, so d(loss)/d(g_S) = 0 by construction. One
        # optimizer step moves B off zero (B itself has non-zero grad).
        loss0 = base(x).logits.square().sum()
        opt.zero_grad()
        loss0.backward()
        assert c.gates.grad is not None
        assert (c.gates.grad[:, 0] != 0).all(), "domain gates dead at step 0"
        assert (c.gates.grad[:, 1] != 0).all(), "user gates dead at step 0"
        b_key = [k for k in c._session_lora if k.endswith("lora_B")][0]
        assert (c._session_lora[b_key].grad != 0).any()
        opt.step()

        # Probe backward: now ALL 84 gates must receive gradient.
        loss = base(x).logits.square().sum()
        opt.zero_grad()
        loss.backward()
        grad = c.gates.grad
        assert grad is not None and grad.shape == (28, 3)
        nonzero = int((grad != 0).sum())
        assert nonzero == 84, f"{nonzero}/84 gates received gradient"
        # Session LoRA A and B both trainable through the wired path.
        for k, p in c._session_lora.items():
            assert p.grad is not None and (p.grad != 0).any(), k

    def test_gates_trainable_via_optimizer(self) -> None:
        base, c, _ = _wired_composer(n_layers=4)
        opt = torch.optim.AdamW(c.get_trainable_parameters(), lr=1e-2)
        x = torch.randint(0, 20, (2, 5))
        before = c.gates.detach().clone()
        for _ in range(3):
            loss = base(x).logits.square().sum()
            opt.zero_grad()
            loss.backward()
            opt.step()
        assert not torch.allclose(before, c.gates.detach())


class TestComputeWeightDeltas:
    def test_diff_keys_and_values(self, tmp_path) -> None:
        from safetensors.torch import save_file

        w = torch.arange(12, dtype=torch.float32).reshape(3, 4)
        base = {
            "model.layers.0.self_attn.q_proj.weight": w.clone(),
            "model.layers.0.self_attn.v_proj.weight": w.clone(),
            "model.layers.0.mlp.up_proj.weight": w.clone(),  # not a target
        }
        merged = {k: v + 1.0 for k, v in base.items()}
        bp = tmp_path / "base.safetensors"
        mp = tmp_path / "merged.safetensors"
        save_file(base, str(bp))
        save_file(merged, str(mp))

        deltas = compute_weight_deltas(str(bp), str(mp))
        assert set(deltas) == {
            "model.layers.0.self_attn.q_proj",
            "model.layers.0.self_attn.v_proj",
        }
        for d in deltas.values():
            assert torch.allclose(d, torch.ones(3, 4))
