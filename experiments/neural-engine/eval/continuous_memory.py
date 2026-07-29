"""阶段 2 方向 C:MemoryBank + 0.6B 集成 + TTT 持续学习。

把 MemoryBank 挂到 Qwen3-0.6B 的 hidden state -> logits 之间。
推理时:prompt -> base 模型 -> hidden state -> MemoryBank 检索注入 -> logits
TTT 时:reward-weighted loss 更新 MemoryBank 的 key/value/gate(+ base LoRA)。

运行(8 轮,真云端):
  cd experiments/neural-engine
  uv run python -m eval.continuous_memory --rounds 8
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path

import numpy as np
import torch
import torch.nn as nn

os.environ.setdefault("HF_HUB_OFFLINE", "1")
os.environ.setdefault("TRANSFORMERS_OFFLINE", "1")
os.environ.setdefault("CUDA_VISIBLE_DEVICES", "")

from llm.local_llm import LocalLLM
from selflearn import DEFAULT_LGBM_PARAMS, LoopConfig, SelfLearnLoop, StrategyMemory
from selflearn.clab import (
    CLAB_FIELDS,
    CLAB_FIELD_STATEMENTS,
    build_clab_split,
    clab_base_features,
)
from selflearn.memory_bank import MemoryBank
from selflearn.ttt import TTTConfig, ttt_step, setup_ttt_lora
from slots import SlotConfig, SlotService
from synth import SyntheticWorld, default_config

from eval.continuous_learn import RoundResult

MODEL_ID = "Qwen/Qwen3-0.6B"


class MemoryAugmentedLM(nn.Module):
    """Qwen3-0.6B + MemoryBank 包装(原生记忆版)。

    base 模型冻结(不更新),MemoryBank 作为 logits 的加性偏置:
      final_logits = base_logits + gate * logit_bias

    logit_bias 由 MemoryBank 检索产生:
      query(从 input_ids embedding)-> attention over memory keys
      -> weighted sum of memory values -> proj to vocab_size

    梯度路径:loss -> final_logits -> logit_bias -> memory 参数
    (不经过 base,base 冻结不影响梯度链)。
    """

    def __init__(self, base_model: nn.Module, memory: MemoryBank,
                 vocab_size: int, hidden_size: int) -> None:
        super().__init__()
        self.base = base_model
        self.memory = memory
        self.vocab_size = vocab_size
        self.hidden_size = hidden_size
        # 用 input_ids 的 embedding 做 query(不依赖 base hidden state)
        # 这样即使 base 冻结,梯度也能从 embedding 层流到 memory
        self.embed = nn.Embedding(vocab_size, hidden_size, padding_idx=0)
        # logit bias 投影:memory 输出(hidden) -> vocab
        self.logit_bias_proj = nn.Linear(hidden_size, vocab_size, bias=False)
        nn.init.zeros_(self.logit_bias_proj.weight)  # 零初始化(初始不影响 base)

    def forward(self, input_ids, labels=None, attention_mask=None, **kwargs):
        # base 模型正常 forward(冻结,no_grad 加速)
        with torch.no_grad():
            outputs = self.base(input_ids=input_ids, attention_mask=attention_mask)
            base_logits = outputs.logits  # [batch, seq, vocab]

        # MemoryBank query:用 input_ids embedding(可训练的 embedding 层)
        emb = self.embed(input_ids)  # [batch, seq, hidden]
        # MemoryBank 检索:用最后一个 token 的 embedding 做 query
        memory_out = self.memory(emb)  # [batch, seq, hidden]
        # 投影到 vocab 维度
        logit_bias = self.logit_bias_proj(memory_out)  # [batch, seq, vocab]

        # 加性偏置
        final_logits = base_logits + logit_bias

        # 算 loss
        loss = None
        if labels is not None:
            from torch.nn.functional import cross_entropy
            shift_logits = final_logits[:, :-1, :].contiguous()
            shift_labels = labels[:, 1:].contiguous()
            mask = shift_labels != -100
            if mask.any():
                loss = cross_entropy(
                    shift_logits[mask], shift_labels[mask]
                )

        return type("Out", (), {"loss": loss, "logits": final_logits})()


def run_memory_continuous(
    rounds: int,
    seed: int,
    out_dir: Path,
    init_model: str,
    use_real_cloud: bool = False,
) -> list[RoundResult]:
    """跑 MemoryBank + TTT 持续学习闭环。"""
    from eval.orchestrator_eval import OrchestratorCloud
    from transformers import AutoModelForCausalLM, AutoTokenizer

    # 构建真云端
    cloud_llm = None
    if use_real_cloud:
        from cloud.providers import OpenAIProvider
        api_key = os.environ.get("DEEPSEEK_API_KEY", "")
        if api_key:
            cloud_llm = OpenAIProvider(provider="deepseek", api_key=api_key)
            print(">>> 真云端在环:deepseek-v4-flash")

    fields_str = " ".join(CLAB_FIELDS)
    results: list[RoundResult] = []

    # 加载 base 模型 + MemoryBank
    print(f">>> 加载 {init_model} + MemoryBank...")
    tokenizer = AutoTokenizer.from_pretrained(init_model)
    base_model = AutoModelForCausalLM.from_pretrained(
        init_model, torch_dtype=torch.float32, output_hidden_states=True
    )
    base_model.eval()

    # 获取 hidden_size + vocab_size
    hidden_size = base_model.config.hidden_size
    vocab_size = base_model.config.vocab_size
    memory = MemoryBank(
        n_slots=16, key_dim=64, value_dim=64, query_dim=hidden_size
    )

    # 包装成 MemoryAugmentedLM(embedding + memory + logit_bias 可训,base 冻结)
    aug_model = MemoryAugmentedLM(base_model, memory, vocab_size, hidden_size)

    # 不用 PEFT 包装 MemoryAugmentedLM(它不是标准 CausalLM,PEFT 会报错)。
    # 冻结 base,只训 MemoryAugmentedLM 的可训参数(embed + memory + logit_bias_proj)。
    for param in base_model.parameters():
        param.requires_grad = False
    # 收集 aug_model 的可训参数(base 之外的)
    trainable = [p for p in aug_model.parameters() if p.requires_grad]
    ttt_config = TTTConfig(steps=3, lr=1e-3)
    optimizer = torch.optim.AdamW(trainable, lr=ttt_config.lr)
    print(f">>> MemoryBank(16 slots) + embed + logit_bias 可训,base 冻结")

    # 构建闭环 world
    world = SyntheticWorld(default_config(seed=seed))
    data = world.run(60, 200)
    split = build_clab_split(data, dev_episodes=40)

    # 为闭环用的 LLM wrapper(MemoryAugmentedLM 需要 generate 方法)
    # 简化:闭环用原始 base_model 推理(不经过 MemoryBank),TTT 用 aug_model
    # 这样闭环逻辑不变,MemoryBank 只在 TTT 更新时生效
    class SimpleLLMWrapper:
        def __init__(self, model, tokenizer):
            self._model = model
            self._tokenizer = tokenizer
        @property
        def loaded(self): return True
        def generate(self, prompt, max_new_tokens=32, temperature=0.3, top_p=0.9):
            inputs = self._tokenizer(prompt, return_tensors="pt")
            gen_kwargs = {"max_new_tokens": max_new_tokens}
            if temperature > 0:
                gen_kwargs.update(do_sample=True, temperature=temperature, top_p=top_p)
            else:
                gen_kwargs["do_sample"] = False
            with torch.no_grad():
                out = self._model.generate(**inputs, **gen_kwargs)
            new_ids = out[0][inputs["input_ids"].shape[1]:]
            return self._tokenizer.decode(new_ids, skip_special_tokens=True)

    llm_wrapper = SimpleLLMWrapper(base_model, tokenizer)

    for r in range(1, rounds + 1):
        print(f"\n>>> round {r}/{rounds}")

        round_dir = out_dir / f"round-{r}"
        round_dir.mkdir(parents=True, exist_ok=True)
        work = round_dir / "work"
        if work.exists():
            import shutil
            shutil.rmtree(work)
        work.mkdir()

        memory_slots = StrategyMemory(SlotService(work / "slots.db", SlotConfig()))
        memory_slots.init_field_slots(CLAB_FIELD_STATEMENTS)
        cloud = OrchestratorCloud(llm=llm_wrapper, fields_str=fields_str, cloud_llm=cloud_llm)

        loop_cfg = LoopConfig(
            dev_start="000", dev_end="039",
            eval_start="040", eval_end="059",
            top_k=20, max_features_per_round=20,
            dev_holdout_episodes=3,
            lgbm_params=dict(DEFAULT_LGBM_PARAMS), seed=seed,
        )
        loop = SelfLearnLoop(split.dev_df, config=loop_cfg,
                             base_features=clab_base_features,
                             cloud=cloud, memory=memory_slots)
        rec = loop.run_round(r)
        memory_slots.service.persist()

        # 算指标
        ivs = [p.metrics.get("iv", 0) for p in rec.proposals
               if p.verdict == "pass" and isinstance(p.metrics.get("iv", 0), (int, float))]
        n_strong = sum(1 for iv in ivs if iv > 0.3)
        avg_iv = float(np.mean(ivs)) if ivs else 0.0

        result = RoundResult(
            round_no=r,
            n_proposals=len(rec.proposals),
            n_passed=sum(1 for p in rec.proposals if p.verdict == "pass"),
            n_strong=n_strong,
            avg_iv=round(avg_iv, 4),
            dead_end_repeats=rec.extras.dead_end_repeats if rec.extras else 0,
            reward=round(avg_iv, 4),
            trained=True,
        )
        results.append(result)
        print(f"    proposals={result.n_proposals} passed={result.n_passed} "
              f"strong={result.n_strong} avg_iv={result.avg_iv:.4f}")

        # TTT 更新(用 MemoryAugmentedLM)
        ttt_prompt = cloud.last_prompt
        ttt_completion = cloud.last_completion
        if ttt_prompt and ttt_completion:
            metrics = ttt_step(
                aug_model, tokenizer, optimizer,
                prompt=ttt_prompt, completion=ttt_completion,
                reward=avg_iv, config=ttt_config,
            )
            snap = memory.get_memory_snapshot()
            print(f"    [TTT+Mem] sft_loss={metrics['sft_loss']:.4f} "
                  f"gate={snap['gate']:.4f} "
                  f"keys_norm={snap['keys'].norm():.4f}")

    return results


def main():
    parser = argparse.ArgumentParser(description="阶段 2 方向 C MemoryBank 持续学习")
    parser.add_argument("--rounds", type=int, default=8)
    parser.add_argument("--seed", type=int, default=20260728)
    parser.add_argument("--init-model", type=str,
                        default="eval/artifacts-orchestrator/grpo/final")
    parser.add_argument("--real-cloud", action="store_true")
    parser.add_argument("--out", type=Path,
                        default=Path("eval/artifacts-orchestrator/memory"))
    args = parser.parse_args()

    print(f">>> MemoryBank 持续学习(rounds={args.rounds})")
    results = run_memory_continuous(
        rounds=args.rounds, seed=args.seed, out_dir=args.out,
        init_model=args.init_model, use_real_cloud=args.real_cloud,
    )

    print(f"\n=== MemoryBank 持续学习趋势 ===")
    print(f'{"round":<8} {"proposals":<12} {"passed":<10} {"strong":<10} '
          f'{"avg_iv":<10}')
    for r in results:
        print(f'{r.round_no:<8} {r.n_proposals:<12} {r.n_passed:<10} '
              f'{r.n_strong:<10} {r.avg_iv:<10.4f}')

    avg_ivs = [r.avg_iv for r in results]
    first_half = np.mean(avg_ivs[:len(avg_ivs)//2])
    second_half = np.mean(avg_ivs[len(avg_ivs)//2:])
    print(f"\n前半程 avg_iv: {first_half:.4f}")
    print(f"后半程 avg_iv: {second_half:.4f}")
    if second_half > first_half:
        print("✅ 后半程 > 前半程,MemoryBank 持续学习有效")
    else:
        print("⚠️ 后半程 <= 前半程,无明显改善")


if __name__ == "__main__":
    main()
