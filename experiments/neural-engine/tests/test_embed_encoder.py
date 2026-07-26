"""Embedder tests (fake-encoder path): shapes, normalization, lazy loading,
singleton thread-safety. No real model is loaded in this file."""

from __future__ import annotations

import subprocess
import sys
import threading
from pathlib import Path

import numpy as np
import pytest

from embed import Embedder
from embed.fake import hash_encode

NEURAL_ENGINE_DIR = Path(__file__).resolve().parent.parent


@pytest.fixture
def embedder() -> Embedder:
    return Embedder(encode_fn=hash_encode)


class TestShapesAndNorms:
    def test_embed_keys_shape(self, embedder: Embedder) -> None:
        out = embedder.embed_keys(["a", "b", "c"])
        assert out.shape == (3, 256)
        assert out.dtype == np.float32

    def test_embed_values_shape(self, embedder: Embedder) -> None:
        out = embedder.embed_values(["a", "b"])
        assert out.shape == (2, 1024)
        assert out.dtype == np.float32

    def test_l2_normalized(self, embedder: Embedder) -> None:
        for out in (embedder.embed_keys(["x", "y"]), embedder.embed_values(["x", "y"])):
            norms = np.linalg.norm(out, axis=1)
            np.testing.assert_allclose(norms, 1.0, rtol=1e-5)

    def test_fake_encoder_deterministic(self, embedder: Embedder) -> None:
        a = embedder.embed_keys(["same text"])
        b = embedder.embed_keys(["same text"])
        np.testing.assert_array_equal(a, b)

    def test_distinct_texts_distinct_vectors(self, embedder: Embedder) -> None:
        out = embedder.embed_keys(["text one", "text two"])
        assert float(out[0] @ out[1]) < 0.5  # hash projection ~ orthogonal


class TestLazyLoading:
    def test_import_does_not_load_sentence_transformers(self) -> None:
        """Importing embed must not pull the 1.2GB model stack."""
        code = (
            "import sys; sys.path.insert(0, '.');"
            "import embed;"
            "assert 'sentence_transformers' not in sys.modules;"
            "assert 'torch' not in sys.modules"
        )
        subprocess.run(
            [sys.executable, "-c", code],
            cwd=NEURAL_ENGINE_DIR,
            check=True,
        )

    def test_constructor_does_not_load_model(self) -> None:
        emb = Embedder()  # real model name, but no encode yet
        assert emb._model is None

    def test_fake_path_never_loads_model(self, embedder: Embedder) -> None:
        embedder.embed_keys(["a"])
        embedder.embed_values(["a"])
        assert embedder._model is None


class TestSingleton:
    def teardown_method(self) -> None:
        Embedder._default = None  # do not leak across tests

    def test_default_returns_same_instance(self) -> None:
        assert Embedder.default() is Embedder.default()

    def test_default_concurrent_single_instance(self) -> None:
        got: list[Embedder] = []

        def grab() -> None:
            got.append(Embedder.default())

        threads = [threading.Thread(target=grab) for _ in range(8)]
        for t in threads:
            t.start()
        for t in threads:
            t.join()
        assert len({id(e) for e in got}) == 1
