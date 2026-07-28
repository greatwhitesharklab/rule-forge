"""阶段 1.5:GRPO 训练编排器(简化动作 + 轻量 reward 代理)。

把所有零件接起来:
  语境 prompt(简化版)-> 0.6B 生成动作 -> parse_simple_action -> proxy_reward
  -> trl GRPOTrainer 更新权重

简化动作格式:"<TOOL> <keyword1> <keyword2> ..."
GRPO 探索空间小(选工具 + 选字段方向),易收敛。

reward 代理:预计算的 DirectionValueTable(方案 B),毫秒级,训练友好。
真执行(GBDT + 免疫系统)留给阶段 1 终证伪。

运行(CPU,慢):
  cd experiments/neural-engine
  uv run python -m eval.grpo_train --steps 10
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path

import numpy as np
import pandas as pd

# 离线模式(防 from_pretrained 联网,跟 test_orchestrator_real_model 一致)
os.environ.setdefault("HF_HUB_OFFLINE", "1")
os.environ.setdefault("TRANSFORMERS_OFFLINE", "1")
# 强制 CPU:GPU 只有 3.6GiB,0.6B + GRPO(ref model + 4 generations)爆显存
# 必须在 import torch 前设,让 torch 看不到 GPU
os.environ["CUDA_VISIBLE_DEVICES"] = ""

from datasets import Dataset

from selflearn.action import parse_simple_action
from selflearn.clab import CLAB_FIELDS, build_clab_split, clab_base_features
from selflearn.reward_proxy import build_direction_value_table, proxy_reward
from slots import SlotConfig, SlotService
from synth import SyntheticWorld, default_config

MODEL_ID = "Qwen/Qwen3-0.6B"


def _build_prompts_and_table(seed: int = 20260728):
    """构建 GRPO 训练数据:prompt 数据集 + 方向价值表。"""
    world = SyntheticWorld(default_config(seed=seed))
    data = world.run(60, 200)
    split = build_clab_split(data, dev_episodes=40)

    feature_df = split.dev_df[list(CLAB_FIELDS)]
    labels = split.dev_df["outcome"].to_numpy().astype(np.int8)

    # 方向价值表(死路档案为空 -- 首轮训练无死路)
    table = build_direction_value_table(feature_df, labels)

    # prompt:简化版语境 + few-shot 示例(base 模型必需,zero-shot 会复读)
    fields_str = " ".join(CLAB_FIELDS)
    prompt = (
        f"信贷审批编排。可用字段:{fields_str}。\n"
        f"残余信号:savings_months(d=0.35),months_employed(d=0.22)。\n"
        f"工具:GBDT(黑箱准)/CART(可解释)。\n"
        f"示例:CART savings_months debt_to_income\n"
        f"示例:GBDT income_volatility\n"
        f"输出一个动作(工具名 + 探索字段,空格分隔,只一行):"
    )
    # GRPO 需要多个 prompt 重复(每个生成 num_generations 个 completion)
    # 阶段 1.5 先用单一 prompt + 多生成,验证收敛
    prompts = [prompt] * 8
    return prompts, table


def _make_reward_func(table):
    """构造 trl GRPOTrainer 的 reward_func。

    trl 调用签名:reward_func(prompts: list[str], completions: list[str], **kwargs)
    -> list[float]
    每个 completion 是 LLM 生成的动作文本,解析后查价值表算 reward。

    2026-07 加探索多样性:跟踪 explored_fields,选新字段给 bonus
    (修根因 3:编排器反复聚焦 platform_loans 的模式坍塌)。
    """

    # 跨 step 跟踪已探索字段(GRPO 多步训练累积)
    explored_fields: set[str] = set()

    def reward_func(prompts, completions, **kwargs) -> list[float]:
        rewards = []
        for i, comp in enumerate(completions):
            action = parse_simple_action(comp)
            r = proxy_reward(action, table,
                             explored_fields=frozenset(explored_fields))
            rewards.append(float(r))
            # 累积探索字段(用这一批里 reward 最高的动作)
            if action is not None and r > 0:
                explored_fields.update(action.direction_keywords)
            # 调试:打印前 3 个
            if i < 3:
                short = repr(comp[:80]) if comp else "(empty)"
                print(f"  [reward_func] comp[{i}]: {short} -> action={action} r={r:.4f}")
        return rewards

    return reward_func


def train_grpo(steps: int = 10, out_dir: str = "eval/artifacts-orchestrator/grpo"):
    """跑 GRPO 训练编排器。

    steps: 训练步数(阶段 1.5 先少跑,看 reward 曲线)
    """
    from trl import GRPOConfig, GRPOTrainer

    print(">>> 构建 prompt + 方向价值表...")
    prompts, table = _build_prompts_and_table()
    print(f"    {len(prompts)} prompts, "
          f"{len(table.single_values)} 单字段, "
          f"{len(table.pair_values)} 字段对")

    # 价值表速览(确认有区分度)
    top_singles = sorted(table.single_values.items(), key=lambda x: -x[1])[:3]
    print(f"    单字段 IV top: {top_singles}")

    dataset = Dataset.from_dict({"prompt": prompts})
    reward_func = _make_reward_func(table)

    print(f">>> GRPO 训练(steps={steps}, model={MODEL_ID})...")
    config = GRPOConfig(
        output_dir=out_dir,
        num_generations=4,           # 每个 prompt 生成 4 个 completion(group size)
        max_completion_length=32,    # 简化动作很短
        per_device_train_batch_size=4,
        learning_rate=1e-5,          # 小 lr,0.6B 上稳
        max_steps=steps,
        temperature=0.9,             # 提高(0.7->0.9),鼓励探索防模式坍塌
        beta=0.01,                   # KL 惩罚(小,让策略能动)
        logging_steps=1,
        save_steps=steps,            # 最后存一次
        report_to="none",            # 不接 wandb
        use_cpu=True,                # GPU OOM(3.6GiB),强制 CPU
        # 2026-07 修根因 1(模式坍塌):开自适应 entropy 鼓励探索
        use_adaptive_entropy=True,
        entropy_coef_max=0.05,       # 小 bonus,不让探索压过价值信号
        entropy_target=0.5,          # 目标熵(比默认 0.2 高,更鼓励多样)
    )

    trainer = GRPOTrainer(
        model=MODEL_ID,
        reward_funcs=reward_func,
        args=config,
        train_dataset=dataset,
    )

    trainer.train()

    # 存模型
    save_path = Path(out_dir) / "final"
    trainer.save_model(str(save_path))
    print(f">>> 模型存到 {save_path}")


def main():
    parser = argparse.ArgumentParser(description="阶段 1.5 GRPO 训练编排器")
    parser.add_argument("--steps", type=int, default=10)
    parser.add_argument("--out", type=str,
                        default="eval/artifacts-orchestrator/grpo")
    args = parser.parse_args()

    train_grpo(steps=args.steps, out_dir=args.out)


if __name__ == "__main__":
    main()
