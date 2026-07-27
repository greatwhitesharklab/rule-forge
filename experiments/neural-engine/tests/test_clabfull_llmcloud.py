"""LocalLLMCloud 测试:手册注入、四形态 JSON 解析、重试降级、提案上限。

小模型臂的产出是「候选」:JSON 解析失败重试一次,再失败降级为空提案
(绝不抛异常进闭环);methodology.md 全文必须出现在 prompt 中(system 段)。
"""

from __future__ import annotations

import json

import pytest

from cloud.contracts import TaskPackage, validate_result
from selflearn.clabfull import LocalLLMCloud, load_methodology


def _task(max_features: int = 3, task_id: str = "selflearn-r01-t") -> TaskPackage:
    return TaskPackage(
        task_id=task_id,
        task_type="feature_proposal",
        context={
            "case_profiles": [{"n": 12, "share_of_dev": 0.01}],
            "existing_features": ["f_a", "f_b"],
            "dead_ends": ["d1"],
            "residual_signals": {"n_missed": 12, "numeric": [
                {"feature": "seq_paste_count", "cohens_d": 0.9,
                 "direction": "missed_higher"}]},
        },
        constraints={"max_features": max_features,
                     "must_be_executable": "pandas over df",
                     "no_future_info": True},
        output_schema={"type": "object"},
    )


def _feature_json(n: int = 2) -> dict:
    return {"features": [
        {"name": f"feat_{i}", "expression": f"(df.seq_paste_count > {i}.0)",
         "rationale": f"机制 {i}"}
        for i in range(n)
    ]}


class FakeGen:
    """记录 prompt 的可编程 generate_fn。"""

    def __init__(self, outputs: list[str]) -> None:
        self.outputs = list(outputs)
        self.prompts: list[str] = []

    def __call__(self, prompt: str) -> str:
        self.prompts.append(prompt)
        return self.outputs.pop(0) if self.outputs else "{}"


class TestMethodologyInjection:
    def test_methodology_full_text_in_prompt(self) -> None:
        gen = FakeGen([json.dumps(_feature_json(), ensure_ascii=False)])
        cloud = LocalLLMCloud(gen)
        cloud.execute(_task())
        assert len(gen.prompts) == 1
        methodology = load_methodology()
        assert methodology in gen.prompts[0]
        # 手册关键章节标志(全文注入的抽样证据)
        for marker in ("信贷特征挖掘方法论", "挖掘判断树", "模式库", "提案格式纪律"):
            assert marker in gen.prompts[0]

    def test_chat_wrap_markers_present(self) -> None:
        gen = FakeGen([json.dumps(_feature_json(), ensure_ascii=False)])
        cloud = LocalLLMCloud(gen)
        cloud.execute(_task())
        assert "<|im_start|>" in gen.prompts[0]
        assert "<|im_end|>" in gen.prompts[0]

    def test_guide_signal_in_prompt(self) -> None:
        """指路信号(残余信号字段名)作为案例材料进入 prompt。"""
        gen = FakeGen([json.dumps(_feature_json(), ensure_ascii=False)])
        cloud = LocalLLMCloud(gen)
        cloud.execute(_task())
        assert "seq_paste_count" in gen.prompts[0]

    def test_field_dictionary_in_prompt(self) -> None:
        gen = FakeGen([json.dumps(_feature_json(), ensure_ascii=False)])
        cloud = LocalLLMCloud(gen)
        cloud.execute(_task())
        # 字段字典让 0.6B 知道可引用哪些列
        assert "device_id" in gen.prompts[0]
        assert "seq_paste_count" in gen.prompts[0]


class TestJsonParsingFourForms:
    def _run(self, text: str) -> dict:
        gen = FakeGen([text])
        cloud = LocalLLMCloud(gen)
        res = cloud.execute(_task())
        validate_result("feature_proposal", res.content)
        return res.content

    def test_clean_json(self) -> None:
        content = self._run(json.dumps(_feature_json(), ensure_ascii=False))
        assert len(content["features"]) == 2

    def test_fenced_json(self) -> None:
        text = "```json\n" + json.dumps(_feature_json(), ensure_ascii=False) + "\n```"
        content = self._run(text)
        assert len(content["features"]) == 2

    def test_chatter_wrapped_json(self) -> None:
        text = ("好的,我来分析这组疑难案例。\n"
                + json.dumps(_feature_json(), ensure_ascii=False)
                + "\n以上是我的提案。")
        content = self._run(text)
        assert len(content["features"]) == 2

    def test_proposals_capped_at_max(self) -> None:
        gen = FakeGen([json.dumps(_feature_json(6), ensure_ascii=False)])
        cloud = LocalLLMCloud(gen, max_proposals=3)
        res = cloud.execute(_task(max_features=6))
        assert len(res.content["features"]) == 3


class TestRetryAndDegrade:
    def test_retry_once_on_parse_failure(self) -> None:
        gen = FakeGen(["这不是 JSON",
                       json.dumps(_feature_json(), ensure_ascii=False)])
        cloud = LocalLLMCloud(gen)
        res = cloud.execute(_task())
        assert len(res.content["features"]) == 2
        assert len(gen.prompts) == 2  # 恰好重试一次
        assert cloud.stats["retries"] == 1
        # 重试 prompt 带错误反馈
        assert "无法解析" in gen.prompts[1] or "error" in gen.prompts[1].lower()

    def test_degrade_to_empty_after_two_failures(self) -> None:
        gen = FakeGen(["垃圾输出", "还是垃圾"])
        cloud = LocalLLMCloud(gen)
        res = cloud.execute(_task())
        assert res.content["features"] == []
        assert len(gen.prompts) == 2
        assert cloud.stats["parse_failures"] == 1

    def test_generate_exception_degrades(self) -> None:
        def boom(_prompt: str) -> str:
            raise RuntimeError("model dead")

        cloud = LocalLLMCloud(boom)
        res = cloud.execute(_task())
        assert res.content["features"] == []
        assert cloud.stats["llm_errors"] == 1

    def test_schema_invalid_items_skipped(self) -> None:
        text = json.dumps({"features": [
            {"name": "ok_feat", "expression": "(df.seq_len > 9)", "rationale": "x"},
            {"name": "", "expression": "(df.seq_len > 9)", "rationale": "空名"},
            {"expression": "(df.seq_len > 9)"},  # 缺 name/rationale
            "not-a-dict",
        ]}, ensure_ascii=False)
        content = self._run_one(text)
        assert [f["name"] for f in content["features"]] == ["ok_feat"]

    def _run_one(self, text: str) -> dict:
        gen = FakeGen([text])
        cloud = LocalLLMCloud(gen)
        return cloud.execute(_task()).content

    def test_stats_track_calls_and_latency(self) -> None:
        gen = FakeGen([json.dumps(_feature_json(), ensure_ascii=False)])
        cloud = LocalLLMCloud(gen)
        cloud.execute(_task())
        assert cloud.stats["calls"] == 1
        assert len(cloud.stats["latency_s"]) == 1
        assert cloud.stats["latency_s"][0] >= 0.0
        assert cloud.stats["prompt_chars"][0] > 0


class TestProvenance:
    def test_provenance_fields(self) -> None:
        gen = FakeGen([json.dumps(_feature_json(), ensure_ascii=False)])
        cloud = LocalLLMCloud(gen)
        res = cloud.execute(_task())
        assert res.provenance.provider == "local-llm"
        assert res.provenance.model == cloud.model_name
        assert res.task_id == "selflearn-r01-t"


class TestFromLlmWrapsGenerate:
    def test_from_llm_uses_chat_wrap(self) -> None:
        captured: dict[str, str] = {}

        class FakeLLM:
            def generate(self, prompt: str, max_new_tokens: int = 64,
                         temperature: float = 0.0, **kw: object) -> str:
                captured["prompt"] = prompt
                captured["max_new_tokens"] = str(max_new_tokens)
                return json.dumps(_feature_json(), ensure_ascii=False)

        cloud = LocalLLMCloud.from_llm(FakeLLM(), max_new_tokens=320)
        cloud.execute(_task())
        assert captured["prompt"].startswith("<|im_start|>")
        assert "<think>" in captured["prompt"]
        assert captured["max_new_tokens"] == "320"
