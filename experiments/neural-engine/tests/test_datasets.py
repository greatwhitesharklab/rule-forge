"""Dataset-spec config tests: validity, baseline freeze, head isolation."""

import numpy as np
import pandas as pd
import pytest

from memory.hasher import MultiHeadHasher
from training.datasets import (
    credit_card_default_spec,
    credit_g_spec,
    give_me_some_credit_spec,
)
from training.train import Preprocessor

ALL_SPECS = [credit_g_spec, give_me_some_credit_spec, credit_card_default_spec]


@pytest.mark.parametrize("make_spec", ALL_SPECS, ids=lambda f: f().name)
def test_spec_is_internally_consistent(make_spec):
    spec = make_spec()  # validate() runs in the factory and would raise
    feats = set(spec.feature_names)
    assert set(spec.numeric_features) <= feats
    for head in spec.heads:
        assert set(head.features) <= feats
        assert head.num_slots == 1 << head.bits
    assert len({h.name for h in spec.heads}) == len(spec.heads)


def test_credit_g_config_frozen_for_baseline():
    """The credit-g head/numeric config pins the Phase-1 baseline; changing
    it must be a deliberate act (update the baseline metrics too)."""
    spec = credit_g_spec()
    assert [h.name for h in spec.heads] == ["checking", "history", "loan", "profile"]
    assert spec.heads[0].features == ("checking_status",)
    assert spec.heads[2].features == ("credit_amount", "duration")
    assert spec.heads[3].features == ("personal_status", "purpose")
    assert spec.n_bins == 4
    assert "credit_amount" in spec.numeric_features


def test_head_configs_are_isolated_between_datasets():
    """Each dataset declares its own heads; specs are independent objects."""
    g, c = credit_g_spec(), give_me_some_credit_spec()
    g_heads = {h.name for h in g.heads}
    c_heads = {h.name for h in c.heads}
    assert g_heads.isdisjoint(c_heads)
    # Re-building a spec must not leak mutations across instances.
    assert credit_g_spec() == g
    assert give_me_some_credit_spec() == c


def test_gmsc_heads_hash_on_synthetic_frame():
    spec = give_me_some_credit_spec()
    rng = np.random.default_rng(0)
    df = pd.DataFrame(
        {f: rng.uniform(0, 10, size=64) for f in spec.numeric_features}
    )
    hasher = MultiHeadHasher(list(spec.heads), n_bins=spec.n_bins).fit(
        df, list(spec.numeric_features)
    )
    ids, patterns = hasher.address_batch(df)
    assert ids.shape == (64, len(spec.heads))
    # Batch addressing must agree with the row-wise path.
    row_ids, row_patterns = hasher.address_row(df.iloc[0])
    assert list(ids[0]) == [row_ids[h.name] for h in spec.heads]
    assert patterns[0] == row_patterns
    # Numeric features are binned into tokens, not raw floats.
    assert "bin" in patterns[0]["late90"]


def test_preprocessor_median_impute_and_clip():
    df = pd.DataFrame({"x": [1.0, 2.0, np.nan, 100.0] * 25, "c": list("ab") * 50})
    prep = Preprocessor(["x"]).fit(df)
    # Median of [1,2,100] = 2; NaN must be gone after transform.
    _, num = prep.transform(df)
    assert not np.isnan(num).any()
    out = pd.DataFrame({"x": [np.nan, 1e9], "c": ["a", "zz"]})
    cat, num2 = prep.transform(out)
    assert not np.isnan(num2).any()
    # The 1e9 outlier is winsorized to the train 99.5% clip bound, and the
    # unseen category maps to code 0.
    hi = prep.scaler.transform([[min(1e9, prep.clip_hi[0])]])[0, 0]
    assert num2[1, 0] == pytest.approx(hi)
    assert cat[1, 0] == 0
