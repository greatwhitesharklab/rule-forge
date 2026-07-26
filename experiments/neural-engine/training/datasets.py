"""Declarative per-dataset configuration (data-driven heads, ARCHITECTURE D1).

Each dataset is described by a ``DatasetSpec``: its feature columns, which of
them are numeric (hasher binning + standardization), the K hash heads as
named feature subsets, and the binning resolution. Adding a dataset means
adding a spec, not touching the training pipeline.

Loaders return (features_df, labels, source_string); every openml fetch has
a fallback chain so the pipeline stays runnable offline (the actual source
used is always recorded in metrics.json).
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import pandas as pd
from sklearn.datasets import fetch_openml

from memory.hasher import HashHead


@dataclass(frozen=True)
class DatasetSpec:
    """Everything the pipeline needs to know about a dataset."""

    name: str
    feature_names: tuple[str, ...]
    numeric_features: tuple[str, ...]
    heads: tuple[HashHead, ...]
    n_bins: int = 4

    def validate(self) -> "DatasetSpec":
        """Config sanity: heads must only reference declared features."""
        feats = set(self.feature_names)
        if not set(self.numeric_features) <= feats:
            raise ValueError(f"{self.name}: numeric feature not in feature_names")
        names = [h.name for h in self.heads]
        if len(names) != len(set(names)):
            raise ValueError(f"{self.name}: duplicate head names")
        for h in self.heads:
            missing = set(h.features) - feats
            if missing:
                raise ValueError(f"{self.name}: head {h.name} uses unknown {missing}")
        return self


# ------------------------------------------------------------------ credit-g

CREDIT_G_NUMERIC = (
    "duration", "credit_amount", "installment_commitment",
    "residence_since", "age", "existing_credits", "num_dependents",
)
CREDIT_G_CATEGORICAL = (
    "checking_status", "credit_history", "purpose", "savings_status",
    "employment", "personal_status", "other_parties", "property_magnitude",
    "other_payment_plans", "housing", "job", "own_telephone", "foreign_worker",
)


def load_credit_g(seed: int) -> tuple[pd.DataFrame, np.ndarray, str]:
    """German Credit (bad=1 positive). Falls back to data_id=31, then to
    synthetic data (the fallback is reported in metrics.json)."""
    try:
        ds = fetch_openml("credit-g", version=1, as_frame=True, parser="auto")
        source = "openml:credit-g(v1)"
    except Exception as e1:  # noqa: BLE001 - network/parser failures fall through
        try:
            ds = fetch_openml(data_id=31, as_frame=True, parser="auto")
            source = "openml:data_id=31"
        except Exception as e2:  # noqa: BLE001
            print(f"openml failed ({e1!r}; {e2!r}); using synthetic fallback")
            from sklearn.datasets import make_classification

            x, y = make_classification(
                n_samples=1000, n_features=20, n_informative=8,
                weights=[0.7, 0.3], random_state=seed,
            )
            df = pd.DataFrame(x, columns=[f"f{i}" for i in range(20)])
            return df, y.astype(int), "synthetic:make_classification"
    df = ds.frame.copy()
    y = (df.pop("class").astype(str) == "bad").astype(int).to_numpy()
    return df, y, source


def credit_g_spec() -> DatasetSpec:
    """Phase-1 baseline config; heads/numerics frozen for reproducibility."""
    return DatasetSpec(
        name="credit-g",
        feature_names=CREDIT_G_CATEGORICAL + CREDIT_G_NUMERIC,
        numeric_features=CREDIT_G_NUMERIC,
        heads=(
            HashHead("checking", ("checking_status",), bits=10),
            HashHead("history", ("credit_history",), bits=10),
            HashHead("loan", ("credit_amount", "duration"), bits=12),
            HashHead("profile", ("personal_status", "purpose"), bits=12),
        ),
        n_bins=4,
    ).validate()


# ------------------------------------------------------- GiveMeSomeCredit

GMSC_NUMERIC = (
    "RevolvingUtilizationOfUnsecuredLines", "age",
    "NumberOfTime30-59DaysPastDueNotWorse", "DebtRatio", "MonthlyIncome",
    "NumberOfOpenCreditLinesAndLoans", "NumberOfTimes90DaysLate",
    "NumberRealEstateLoansOrLines", "NumberOfTime60-89DaysPastDueNotWorse",
    "NumberOfDependents",
)


def load_give_me_some_credit() -> tuple[pd.DataFrame, np.ndarray, str]:
    """Kaggle GiveMeSomeCredit via openml (150k, bad = SeriousDlqin2yrs).

    The openml v1 frame renames the label to FinancialDistressNextTwoYears
    with Yes/No categories; both namings are accepted. Label 1 = experienced
    90+ days delinquency within two years (~6.7% positive).
    """
    ds = fetch_openml("GiveMeSomeCredit", version=1, as_frame=True, parser="auto")
    df = ds.frame.copy()
    label_col = next(
        c for c in ("SeriousDlqin2yrs", "FinancialDistressNextTwoYears")
        if c in df.columns
    )
    raw = df.pop(label_col)
    if raw.dtype.name == "category" or raw.dtype == object:
        y = (raw.astype(str) == "Yes").astype(int).to_numpy()
    else:
        y = raw.astype(int).to_numpy()
    return df[list(GMSC_NUMERIC)].copy(), y, "openml:GiveMeSomeCredit(v1)"


def give_me_some_credit_spec() -> DatasetSpec:
    """All-numeric config; heads are business-meaningful binned crosses.

    10 quantile bins per feature (vs 4 for credit-g): 90k train rows give
    hundreds of samples per occupied cell, so slot confidence can actually
    separate — the point of the scale-up.
    """
    u, d30, d90 = GMSC_NUMERIC[0], GMSC_NUMERIC[2], GMSC_NUMERIC[6]
    debt, inc, lines = GMSC_NUMERIC[3], GMSC_NUMERIC[4], GMSC_NUMERIC[5]
    return DatasetSpec(
        name="give-me-some-credit",
        feature_names=GMSC_NUMERIC,
        numeric_features=GMSC_NUMERIC,
        heads=(
            # single-feature: worst delinquency (strongest raw signal)
            HashHead("late90", (d90,), bits=10),
            # crosses: utilization x recent lateness, leverage x income
            # band, lifecycle (age) x credit exposure
            HashHead("util_x_late", (u, d30), bits=12),
            HashHead("debt_x_income", (debt, inc), bits=12),
            HashHead("age_x_lines", ("age", lines), bits=12),
        ),
        n_bins=10,
    ).validate()


# ------------------------------------------- fallback: credit card default

# openml default-of-credit-card-clients(v1) ships anonymized x1..x23 columns;
# the mapping below is the published UCI schema order.
CCD_RENAME = {
    "x1": "LIMIT_BAL", "x2": "SEX", "x3": "EDUCATION", "x4": "MARRIAGE",
    "x5": "AGE", "x6": "PAY_0", "x12": "BILL_AMT1", "x18": "PAY_AMT1",
}
CCD_NUMERIC = ("LIMIT_BAL", "AGE", "PAY_0", "BILL_AMT1", "PAY_AMT1")


def load_credit_card_default() -> tuple[pd.DataFrame, np.ndarray, str]:
    """UCI default of credit card clients (30k) — last-resort fallback."""
    ds = fetch_openml(
        "default-of-credit-card-clients", version=1, as_frame=True, parser="auto"
    )
    df = ds.frame.copy().rename(columns=CCD_RENAME)
    y = df.pop("y").astype(int).to_numpy()
    return df[list(CCD_NUMERIC)].copy(), y, "openml:default-of-credit-card-clients(v1)"


def credit_card_default_spec() -> DatasetSpec:
    return DatasetSpec(
        name="default-of-credit-card-clients",
        feature_names=CCD_NUMERIC,
        numeric_features=CCD_NUMERIC,
        heads=(
            HashHead("pay0", ("PAY_0",), bits=10),
            HashHead("limit_x_age", ("LIMIT_BAL", "AGE"), bits=12),
            HashHead("bill_x_pay", ("BILL_AMT1", "PAY_AMT1"), bits=12),
        ),
        n_bins=10,
    ).validate()


# ------------------------------------------------------------------ registry

def resolve(name: str, seed: int) -> tuple[DatasetSpec, pd.DataFrame, np.ndarray, str]:
    """Resolve a dataset name to (spec, features, labels, source).

    ``give-me-some-credit`` degrades gracefully: openml name -> data_id
    45554 -> default-of-credit-card-clients (its own spec), and the chosen
    source is reported.
    """
    if name == "credit-g":
        spec = credit_g_spec()
        df, y, source = load_credit_g(seed)
        return spec, df, y, source
    if name == "give-me-some-credit":
        try:
            df, y, source = load_give_me_some_credit()
            return give_me_some_credit_spec(), df, y, source
        except Exception as e1:  # noqa: BLE001 - try the openml data id
            try:
                ds = fetch_openml(data_id=45554, as_frame=True, parser="auto")
                df = ds.frame.copy()
                label = next(
                    c for c in ("SeriousDlqin2yrs", "FinancialDistressNextTwoYears")
                    if c in df.columns
                )
                y = (df.pop(label).astype(str) == "Yes").astype(int).to_numpy()
                return (
                    give_me_some_credit_spec(),
                    df[list(GMSC_NUMERIC)].copy(), y, "openml:data_id=45554",
                )
            except Exception as e2:  # noqa: BLE001
                print(f"GiveMeSomeCredit failed ({e1!r}; {e2!r}); "
                      "falling back to default-of-credit-card-clients")
                spec = credit_card_default_spec()
                df, y, source = load_credit_card_default()
                return spec, df, y, source
    raise ValueError(f"unknown dataset: {name}")


DATASET_NAMES: tuple[str, ...] = ("credit-g", "give-me-some-credit")
