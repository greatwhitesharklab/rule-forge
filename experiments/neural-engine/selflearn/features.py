"""L2 特征运行时注册:表达式 -> scoring 注册表(§8.2 定义形式,§8.4 门槛)。

安全模型与 verify.sandbox 同一条 AST 白名单:编译期先过
``check_expression_ast``,通过后才允许在进程内求值(训练/推理共用这一份
计算代码,§8.3 铁律一)。verify 阶段的子进程沙箱验证照旧先行,这里的
进程内求值只发生在验证通过之后 —— 两道门,同一个白名单。
"""

from __future__ import annotations

import builtins

import numpy as np
import pandas as pd

from scoring.features import FeatureFn, FeatureRegistry, FeatureSpec
from verify.sandbox import SAFE_BUILTINS, check_expression_ast

_EPS = 1e-6


def compile_l2_expression(expression: str) -> FeatureFn:
    """把云端特征表达式编译成注册表特征函数;AST 白名单不过直接 ValueError。"""
    violations = check_expression_ast(expression)
    if violations:
        raise ValueError("ast whitelist: " + "; ".join(violations))
    code = compile(expression, "<l2-feature>", "eval")

    def fn(df: pd.DataFrame) -> pd.Series:
        env = {
            "__builtins__": {name: getattr(builtins, name) for name in SAFE_BUILTINS},
            "df": df,
            "np": np,
            "pd": pd,
        }
        arr = np.asarray(eval(code, env, {}), dtype=np.float64)  # noqa: S307
        if arr.ndim == 0:
            arr = np.full(len(df), float(arr))
        return pd.Series(arr, index=df.index)

    return fn


def register_l2_feature(
    registry: FeatureRegistry,
    *,
    name: str,
    expression: str,
    rationale: str,
    author: str,
    version: str = "1.0",
) -> FeatureSpec:
    """验证通过的特征入库:L2 级,author=云端 provenance,docstring=假设陈述。"""
    fn = compile_l2_expression(expression)
    fn.__doc__ = rationale
    spec = FeatureSpec(
        name=name, version=version, author=author, level="L2",
        assumption=rationale.strip(), func=fn,
    )
    return registry.register(spec)


def max_abs_correlation(values: np.ndarray, ref: pd.DataFrame) -> float:
    """新特征与现有特征矩阵的最大 |Pearson 相关|(§8.4 增量价值门槛)。

    逐列 pairwise-complete(NaN 对剔除);空参考矩阵返回 0.0。
    """
    if ref.shape[1] == 0:
        return 0.0
    s = pd.Series(np.asarray(values, dtype=np.float64))
    if s.std() == 0.0:  # 常数特征:相关无定义(区分度门槛会先拒,这里防御)
        return 0.0
    best = 0.0
    for col in ref.columns:
        if ref[col].std() == 0.0:
            continue
        c = s.corr(ref[col])
        if np.isfinite(c):
            best = max(best, abs(float(c)))
    return best
