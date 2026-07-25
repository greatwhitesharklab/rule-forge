"""NeuralCreditNet forward / gate / memory-miss tests (D3/D5)."""

import numpy as np
import torch

from memory.table import MemoryTable
from model.net import FeatureSpec, NeuralCreditNet


def make_net() -> tuple[NeuralCreditNet, torch.Tensor, torch.Tensor, torch.Tensor]:
    rng = np.random.default_rng(0)
    spec = FeatureSpec(
        cat_features=["c1", "c2"], cat_vocab_sizes=[5, 4],
        num_features=["n1", "n2"], embed_dim=4,
    )
    table = MemoryTable(["h1", "h2"], [16, 16], 8)
    slot_ids = np.array([[1, 2], [3, 2], [1, 4]], dtype=np.int64)
    emb = rng.standard_normal((3, 8)).astype(np.float32)
    labels = np.array([0, 1, 0])
    patterns = [
        {"h1": "a", "h2": "x"}, {"h1": "b", "h2": "x"}, {"h1": "a", "h2": "y"},
    ]
    table.build(slot_ids, emb, labels, patterns)
    net = NeuralCreditNet(spec, table, hidden_dim=32, backbone_dim=16)

    cat = torch.tensor([[1, 2], [0, 0], [3, 1]], dtype=torch.long)
    num = torch.randn(3, 2)
    sids = torch.tensor([[1, 2], [15, 15], [3, 4]], dtype=torch.long)
    return net, cat, num, sids


def test_forward_shapes_and_gate_range():
    net, cat, num, sids = make_net()
    logit, trace = net(cat, num, sids)
    assert logit.shape == (3,)
    assert trace.gates.shape == (3, 2)
    assert trace.slot_ids.shape == (3, 2)
    assert torch.all(trace.gates >= 0) and torch.all(trace.gates <= 1)
    assert torch.all(trace.prob >= 0) and torch.all(trace.prob <= 1)
    assert trace.memory_miss.shape == (3,)


def test_missed_slot_gate_is_zero():
    net, cat, num, sids = make_net()
    _, trace = net(cat, num, sids)
    # Sample 1 addresses slots (15, 15) which were never built.
    assert not bool(trace.hits[1].any())
    assert float(trace.gates[1].sum()) == 0.0
    assert bool(trace.memory_miss[1])


def test_memory_miss_when_all_confidence_low():
    net, cat, num, sids = make_net()
    # Zero out all confidence/freshness signals -> every gate input is the
    # bias-only path; with the hit mask intact but stats empty the miss flag
    # must be observable rather than silently gated in.
    for head in net.head_names:
        getattr(net, f"mem_{head}_conf").zero_()
        getattr(net, f"mem_{head}_hit").zero_()
    _, trace = net(cat, num, sids)
    assert bool(trace.memory_miss.all())
    assert float(trace.gates.sum()) == 0.0


def test_memory_buffers_are_frozen():
    net, _, _, _ = make_net()
    # Memory slots live in buffers (mem_<head>_*), never in the optimizer;
    # trainable layers like mem_proj are backbone, not memory content.
    slot_params = [
        n for n, _ in net.named_parameters()
        if any(n.startswith(f"mem_{h}_") for h in net.head_names)
    ]
    assert slot_params == []
    buffer_names = {n for n, _ in net.named_buffers()}
    assert "mem_h1_proto" in buffer_names


def test_deterministic_forward():
    net, cat, num, sids = make_net()
    net.eval()
    with torch.no_grad():
        l1, _ = net(cat, num, sids)
        l2, _ = net(cat, num, sids)
    assert torch.allclose(l1, l2)
