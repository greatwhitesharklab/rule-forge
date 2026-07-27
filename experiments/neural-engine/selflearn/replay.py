"""replay 云端模式:既往验证通过的特征固化重放(自动 N 轮 / 回归测试用)。

replay_features.json 格式:

```json
{
  "version": 1,
  "rounds": [
    {"round": 1, "features": [
      {"name": "dti_x_amnt", "expression": "df.dti * df.loan_amnt",
       "rationale": "高杠杆大额借款更危险"}
    ]},
    {"round": 2, "features": []}
  ]
}
```

round 编号从 1 开始,与闭环 task_id 的轮次前缀(``selflearn-rNN``)一一对应;
task_id 可带运行后缀(``selflearn-rNN-<run_id>``,跨运行防冲突),对齐只看
轮次前缀,replay 文件格式不变;缺轮 = 该轮空提案(no-op)。格式错误一律
fail-fast(ReplayFormatError),绝不静默吞掉 —— replay 文件的读者包括
未来的回归测试。
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

from cloud.contracts import Provenance, TaskPackage, TaskResult, validate_result

_NAME_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")
# Accept both the legacy "selflearn-rNN" and the run-scoped
# "selflearn-rNN-<run_id>" format; only the round number drives alignment.
_TASK_ID_RE = re.compile(r"^selflearn-r(\d+)(?:-[0-9A-Za-z-]+)?$")


class ReplayFormatError(ValueError):
    """replay 文件格式违反约定。"""


@dataclass(frozen=True)
class ReplayFeature:
    name: str
    expression: str
    rationale: str


def _parse_feature(raw: object, where: str) -> ReplayFeature:
    if not isinstance(raw, dict):
        raise ReplayFormatError(f"{where}: feature must be an object")
    for field_name in ("name", "expression", "rationale"):
        if not isinstance(raw.get(field_name), str) or not raw[field_name].strip():
            raise ReplayFormatError(
                f"{where}: feature requires a non-empty string {field_name!r}"
            )
    if not _NAME_RE.match(raw["name"]):
        raise ReplayFormatError(
            f"{where}: invalid feature name {raw['name']!r} "
            "(must match [A-Za-z_][A-Za-z0-9_]*)"
        )
    return ReplayFeature(raw["name"], raw["expression"], raw["rationale"])


def load_replay(path: str | Path) -> dict[int, list[ReplayFeature]]:
    """加载并校验 replay 文件;返回 {round_no: [ReplayFeature, ...]}。"""
    path = Path(path)
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ReplayFormatError("replay file must be a JSON object")
    rounds_raw = data.get("rounds", [])
    if not isinstance(rounds_raw, list):
        raise ReplayFormatError("'rounds' must be a list")
    out: dict[int, list[ReplayFeature]] = {}
    for entry in rounds_raw:
        if not isinstance(entry, dict) or not isinstance(entry.get("round"), int) \
                or isinstance(entry.get("round"), bool) or entry["round"] < 1:
            raise ReplayFormatError("each round needs an integer 'round' >= 1")
        round_no = entry["round"]
        if round_no in out:
            raise ReplayFormatError(f"duplicate round {round_no}")
        features_raw = entry.get("features", [])
        if not isinstance(features_raw, list):
            raise ReplayFormatError(f"round {round_no}: 'features' must be a list")
        feats = [_parse_feature(f, f"round {round_no}") for f in features_raw]
        names = [f.name for f in feats]
        if len(set(names)) != len(names):
            raise ReplayFormatError(f"round {round_no}: duplicate feature name")
        out[round_no] = feats
    return out


class ReplayProvider:
    """与 CloudLLM 同构的重放执行器:按 task_id 轮次返回固化特征。

    走与真云端一致的返回契约(validate_result 照过),provenance 如实
    标注 provider=replay —— 重放不是云端产出,审计上不冒充。
    """

    provider_name = "replay"
    model_name = "replay-file"

    def __init__(
        self,
        rounds: dict[int, list[ReplayFeature]],
        *,
        source: str,
    ) -> None:
        self._rounds = rounds
        self._source = source

    def execute(self, task: TaskPackage) -> TaskResult:
        m = _TASK_ID_RE.match(task.task_id)
        if m is None:
            raise ReplayFormatError(
                "replay provider expects task_id 'selflearn-rNN[-<run_id>]', "
                f"got {task.task_id!r}"
            )
        feats = self._rounds.get(int(m.group(1)), [])
        content = {
            "features": [
                {"name": f.name, "expression": f.expression, "rationale": f.rationale}
                for f in feats
            ]
        }
        validate_result(task.task_type, content)  # 与真云端同一返回合约
        return TaskResult(
            task_id=task.task_id,
            task_type=task.task_type,
            content=content,
            provenance=Provenance(
                provider=self.provider_name,
                model=self.model_name,
                model_version=self._source,
                timestamp=datetime.now(timezone.utc).isoformat(),
                prompt_hash="replay",
                cost_tokens=0,
            ),
        )
