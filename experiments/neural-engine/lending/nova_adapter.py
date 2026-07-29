"""Nova 真实信贷数据适配器。

把 nova_loan_episodes.csv 转成编排器闭环能消费的格式。
对接 CLAB 的接口(dev_df + labels + episode 切分),让闭环代码不用改。

数据来源:nova_loan(prod 只读),9866 条,2025-12 ~ 2026-07。
y:outcome_settled(0=good, 1=bad),bad_rate=35.6%。

时间红线:apply_date 切 episode(月),matured_date 判断是否成熟。
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import pandas as pd

# Nova 特征白名单(申请时刻可得)
# 跟 lending/prepare.py 的 FEATURE_COLS 同语义,但字段名是 Nova 的
NOVA_FEATURE_COLS: tuple[str, ...] = (
    "loan_amount",           # 提现金额
    "loan_terms",            # 期数(1-4,7天周期)
    "monthly_income",        # 收入档位(0-10)
    "employment_duration",   # 在职时长档位(0-5)
    "edu_level",             # 学历档位(0-6)
    "age",                   # 年龄
    "outstanding_loan_count",   # 多头借贷平台数
    "outstanding_loan_amount",  # 未结清金额(档位)
    "loans_due_today_count",    # 今日到期贷款数
    "loans_overdue_today_amount",  # 今日逾期金额(档位)
)

# 类别字段(需 one-hot 或 ordinal 编码)
NOVA_CATEGORICAL_COLS: tuple[str, ...] = (
    "gender",              # H/M
    "device_is_emulator",  # True/False(欺诈信号)
    "customer_segment",    # NEW/RETURNING
)

# y 列
NOVA_LABEL_COL = "outcome_settled"

# episode 切分列(按月)
NOVA_EPISODE_COL = "apply_date"


@dataclass
class NovaSplit:
    """Nova 数据切分结果(跟 CLAB ClabSplit 接口一致)。"""

    dev_df: pd.DataFrame       # episode + outcome + 特征
    dev_case_idx: np.ndarray   # 行索引(对齐用)
    dev_episodes: int


def _encode_categorical(df: pd.DataFrame) -> pd.DataFrame:
    """类别字段编码:gender/device_is_emulator/customer_segment -> 数值。"""
    df = df.copy()
    # gender: H/M -> 0/1
    df["gender"] = df["gender"].map({"H": 0, "M": 1}).fillna(-1).astype(float)
    # device_is_emulator: True/False -> 1/0(缺失填 0 = 非模拟器)
    df["device_is_emulator"] = df["device_is_emulator"].fillna(False).astype(bool).astype(int).astype(float)
    # customer_segment: NEW/RETURNING -> 1/0
    df["customer_segment"] = df["customer_segment"].map({"NEW": 1, "RETURNING": 0}).fillna(0).astype(float)
    return df


def _make_episode(df: pd.DataFrame) -> pd.Series:
    """从 apply_date 生成 episode 标签(YYYY-MM 格式,跟 CLAB 的 000 对齐)。

    Nova 的 apply_date 是 datetime,按月切分。
    episode 用月序号(0-based),转成 3 位字符串跟 CLAB 一致。
    """
    dates = pd.to_datetime(df[NOVA_EPISODE_COL])
    months = dates.dt.to_period("M")
    unique_months = sorted(months.unique())
    month_to_ep = {m: f"{i:03d}" for i, m in enumerate(unique_months)}
    return months.map(month_to_ep)


def build_nova_split(
    csv_path: str = "data/nova_loan_episodes.csv",
    *,
    dev_episodes_ratio: float = 0.7,  # 前 70% 月份为 dev,后 30% 为 eval
) -> NovaSplit:
    """加载 Nova CSV,预处理,切分 dev 窗。

    跟 build_clab_split 接口一致:返 dev_df(episode + outcome + 数值特征),
    eval 窗数据不返(时间红线:eval 窗在本函数无入口)。
    """
    df = pd.read_csv(csv_path)

    # 编码类别字段 -> 数值
    df = _encode_categorical(df)

    # 所有特征列(数值 + 编码后的类别)
    all_features = list(NOVA_FEATURE_COLS) + list(NOVA_CATEGORICAL_COLS)

    # 生成 episode
    df["_episode"] = _make_episode(df)

    # 按 episode 排序
    df = df.sort_values("_episode", kind="stable").reset_index(drop=True)

    # 切分 dev 窗
    unique_episodes = sorted(df["_episode"].unique())
    n_dev = max(1, int(len(unique_episodes) * dev_episodes_ratio))
    dev_eps_set = set(unique_episodes[:n_dev])

    mask = df["_episode"].isin(dev_eps_set)
    dev_df = df[mask].copy()

    # 只 matured 的留下(outcome_settled 非 NA)
    dev_df = dev_df[dev_df[NOVA_LABEL_COL].notna()].copy()

    # 重命名:跟 CLAB 一致
    dev_df = dev_df.rename(columns={"_episode": "episode", NOVA_LABEL_COL: "outcome"})

    # 只保留 episode + outcome + 特征
    keep_cols = ["episode", "outcome"] + all_features
    dev_df = dev_df[[c for c in keep_cols if c in dev_df.columns]].reset_index(drop=True)

    dev_case_idx = dev_df.index.to_numpy()

    return NovaSplit(
        dev_df=dev_df,
        dev_case_idx=dev_case_idx,
        dev_episodes=n_dev,
    )


def nova_base_features(df: pd.DataFrame) -> tuple[np.ndarray, list[str]]:
    """Nova 基础特征矩阵(跟 clab_base_features 接口一致)。

    返 (X, feature_names),X 是 float64 矩阵。
    """
    all_features = list(NOVA_FEATURE_COLS) + list(NOVA_CATEGORICAL_COLS)
    cols = [c for c in all_features if c in df.columns]
    X = df[cols].to_numpy(dtype=np.float64)
    # 处理 NaN(填 0)
    X = np.nan_to_num(X, nan=0.0)
    return X, cols


# 供 selflearn.clab 接口对接的字段列表
NOVA_FIELDS = NOVA_FEATURE_COLS + NOVA_CATEGORICAL_COLS

# 字段中文描述(供 prompt 用)
NOVA_FIELD_STATEMENTS: dict[str, str] = {
    "loan_amount": "提现申请金额(连续数值)",
    "loan_terms": "贷款期数(1-4,7天周期产品)",
    "monthly_income": "月收入档位(0-10,有序编码)",
    "employment_duration": "在职时长档位(0-5,有序编码)",
    "edu_level": "学历档位(0-6,有序编码)",
    "age": "年龄(18-65)",
    "outstanding_loan_count": "未结清贷款平台数(多头借贷,0-10)",
    "outstanding_loan_amount": "未结清贷款金额(档位编码)",
    "loans_due_today_count": "今日到期贷款数(0-10)",
    "loans_overdue_today_amount": "今日逾期金额(档位编码)",
    "gender": "性别(H=0, M=1)",
    "device_is_emulator": "设备是否模拟器(欺诈信号,0/1)",
    "customer_segment": "客户分群(NEW=1, RETURNING=0)",
}
