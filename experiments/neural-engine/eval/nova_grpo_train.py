"""Nova 数据上的 GRPO 训练。

跟 grpo_train.py 的区别:用 Nova 真实数据(不是 CLAB 合成)构建
方向价值表 + prompt。编排器学到 Nova 的信号结构。

运行(CPU,~12 分钟/步):
  cd experiments/neural-engine
  uv run python -m eval.nova_grpo_train --steps 5
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path

import numpy as np

os.environ.setdefault("HF_HUB_OFFLINE", "1")
os.environ.setdefault("TRANSFORMERS_OFFLINE", "1")
os.environ.setdefault("CUDA_VISIBLE_DEVICES", "")

from datasets import Dataset

from selflearn.action import parse_simple_action
from selflearn.reward_proxy import build_direction_value_table, proxy_reward
from lending.nova_adapter import build_nova_split, nova_base_features, NOVA_FIELDS
from slots import SlotConfig, SlotService

MODEL_ID = "Qwen/Qwen3-0.6B"


def _build_prompts_and_table(seed: int = 20260729):
    """构建 Nova GRPO 训练数据:prompt 数据集 + 方向价值表。"""
    split = build_nova_split()

    feature_df = split.dev_df[list(NOVA_FIELDS)]
    labels = split.dev_df["outcome"].to_numpy().astype(np.int8)

    table = build_direction_value_table(feature_df, labels)

    # Nova 适配的 prompt(用 Nova 字段名 + Nova 示例)
    fields_str = " ".join(NOVA_FIELDS)
    prompt = (
        "信贷审批编排。可用字段:" + fields_str + "。\n"
        "工具:GBDT(黑箱准)/CART(可解释)。\n"
        "示例:CART customer_segment outstanding_loan_count\n"
        "示例:GBDT loan_amount age\n"
        "输出一个动作(工具名 + 探索字段,空格分隔,只一行):"
    )
    prompts = [prompt] * 8
    return prompts, table


def _make_reward_func(table):
    """GRPO reward_func(跟 grpo_train.py 一致,加探索多样性)。"""
    explored_fields: set[str] = set()

    def reward_func(prompts, completions, **kwargs) -> list[float]:
        rewards = []
        for i, comp in enumerate(completions):
            action = parse_simple_action(comp)
            r = proxy_reward(action, table,
                             explored_fields=frozenset(explored_fields))
            rewards.append(float(r))
            if action is not None and r > 0:
                explored_fields.update(action.direction_keywords)
            if i < 3:
                short = repr(comp[:80]) if comp else "(empty)"
                print(f"  [reward_func] comp[{i}]: {short} -> action={action} r={r:.4f}")
        return rewards

    return reward_func


def train_nova_grpo(steps: int, out_dir: str = "eval/artifacts-orchestrator/nova_grpo"):
    """跑 Nova GRPO 训练编排器。"""
    from trl import GRPOConfig, GRPOTrainer

    print(">>> 构建 Nova prompt + 方向价值表...")
    prompts, table = _build_prompts_and_table()
    print(f"    {len(prompts)} prompts, "
          f"{len(table.single_values)} 单字段, "
          f"{len(table.pair_values)} 字段对")

    top_singles = sorted(table.single_values.items(), key=lambda x: -x[1])[:5]
    print(f"    单字段 IV top: {top_singles}")

    dataset = Dataset.from_dict({"prompt": prompts})
    reward_func = _make_reward_func(table)

    print(f">>> Nova GRPO 训练(steps={steps}, model={MODEL_ID})...")
    config = GRPOConfig(
        output_dir=out_dir,
        num_generations=4,
        max_completion_length=32,
        per_device_train_batch_size=4,
        learning_rate=1e-5,
        max_steps=steps,
        temperature=0.7,
        beta=0.01,
        logging_steps=1,
        save_steps=steps,
        report_to="none",
        use_cpu=True,
    )

    trainer = GRPOTrainer(
        model=MODEL_ID,
        reward_funcs=reward_func,
        args=config,
        train_dataset=dataset,
    )

    trainer.train()

    save_path = Path(out_dir) / "final"
    trainer.save_model(str(save_path))
    print(f">>> 模型存到 {save_path}")


def main():
    parser = argparse.ArgumentParser(description="Nova GRPO 训练")
    parser.add_argument("--steps", type=int, default=5)
    parser.add_argument("--out", type=str,
                        default="eval/artifacts-orchestrator/nova_grpo")
    args = parser.parse_args()

    train_nova_grpo(steps=args.steps, out_dir=args.out)


if __name__ == "__main__":
    main()
