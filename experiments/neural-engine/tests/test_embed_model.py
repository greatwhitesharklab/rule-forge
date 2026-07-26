"""Real-model integration tests (Qwen3-Embedding-0.6B, CPU, local HF cache).

Marked `slow_model`: collected by default, skippable via `-m "not slow_model"`.
Loads the 1.2GB model once per module.
"""

from __future__ import annotations

import numpy as np
import pytest

from embed import Embedder

pytestmark = pytest.mark.slow_model

# Semantically close pair (same risk pattern, different wording) vs. an
# opposite-conclusion credit statement. Margin 0.08 is conservative:
# measured diff is 0.128 (1024-d) / 0.112 (256-d) on CPU.
SIMILAR_A = "现金流紧张且多头借贷的申请人违约倾向显著上升"
SIMILAR_B = "负债压力大、在多个平台都有借款的申请人违约风险明显更高"
OPPOSITE = "信用记录优秀、还款来源充足的优质客户违约可能性极低"


@pytest.fixture(scope="module")
def embedder() -> Embedder:
    return Embedder(device="cpu")


def test_output_shapes_and_dtype(embedder: Embedder) -> None:
    keys = embedder.embed_keys(["测试文本"])
    values = embedder.embed_values(["测试文本"])
    assert keys.shape == (1, 256) and keys.dtype == np.float32
    assert values.shape == (1, 1024) and values.dtype == np.float32
    np.testing.assert_allclose(
        np.linalg.norm(values, axis=1), 1.0, rtol=1e-4
    )


def test_semantic_discrimination(embedder: Embedder) -> None:
    embs = embedder.embed_values([SIMILAR_A, SIMILAR_B, OPPOSITE])
    sim_close = float(embs[0] @ embs[1])
    sim_opposite = float(embs[0] @ embs[2])
    assert sim_close - sim_opposite > 0.08


def test_key_dim_also_discriminates(embedder: Embedder) -> None:
    embs = embedder.embed_keys([SIMILAR_A, SIMILAR_B, OPPOSITE])
    sim_close = float(embs[0] @ embs[1])
    sim_opposite = float(embs[0] @ embs[2])
    assert sim_close > sim_opposite
