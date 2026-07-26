"""P2 acceptance runner (design doc §5): welded Engram gate α + LoRA promotion.

Usage (cwd = experiments/neural-engine):

    uv run python -m eval.p2_acceptance [--out eval/artifacts-p2] [--steps 80]
        [--quick]

Pipeline (all CPU, real Qwen3-0.6B + Qwen3-Embedding-0.6B from the local HF
cache):

1. Warmup: a synthetic world's cases are written to the slot library in
   canonical mode with outcome reflow (reputations differentiated).
2. Joint training: LoRA (r16, design §1.4 spec) + mem_out + gate under one
   optimizer on the distill set (case summary -> verdict pairs; memory hits
   staged per case from the slot store). Base weights are snapshot-guarded.
3. Gate probe (criterion 1): related vs unrelated context batches through
   the welded layer; last_gates distributions -> histogram PNG + json stats.
   PASS iff mean(related) > 0.7 and mean(unrelated) < 0.2.
4. Promotion (criterion 2): the trained challenger adapter vs the raw base
   on a replay set (recent cases) + an old-regime probe subset, judged by
   the real PromotionGate (NLL doors, NT < 5%). PASS iff promoted.

Exit code: 0 on overall PASS, 1 otherwise (FAIL is a legitimate outcome —
measured numbers are reported either way).
"""

from __future__ import annotations

import argparse
import json
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import torch

from embed import Embedder, SemanticMemory, case_row_from_casebook
from embed.canonicalize import canonicalize, experience_text
from eval.arms import EmbeddingCache, warmup_memory
from eval.p2_common import (
    OFF_DOMAIN_TEXTS,
    P2Config,
    StagedMemoryReader,
    build_joint_optimizer,
    build_p2_metrics,
    build_probe_sets,
    collate_uniform,
    gate_verdict,
    hits_for_key,
    length_grouped_batches,
    plot_gate_hist,
    run_gate_probe,
    summarize_gates,
    write_metrics,
)
from llm import LocalLLM, MemoryInjection
from lora import (
    CaseRecord,
    DistillConfig,
    DistillPair,
    PromotionGate,
    build_distill_set,
    encode_pair,
)
from lora.train import assert_base_unchanged, snapshot_base_params
from slots import SlotService
from synth import SyntheticWorld, default_config


def _case_records(world, case_idx: np.ndarray) -> list[CaseRecord]:
    """Canonical-text summary -> verdict records for the given case indices."""
    cb = world.casebook
    out: list[CaseRecord] = []
    for i in case_idx:
        i = int(i)
        row = case_row_from_casebook(cb, i)
        out.append(
            CaseRecord(
                case_id=str(int(cb.case_ids[i])),
                summary=canonicalize(row),
                outcome="bad" if int(world.ledger.outcome[i]) == 1 else "good",
                regime=str(cb.regime_tag[i]),
            )
        )
    return out


def _encode_prompts(tokenizer, texts: list[str], max_len: int) -> list[dict]:
    """Prompt-only encodings (left-truncated) for the gate probe."""
    out = []
    for text in texts:
        ids = tokenizer(text, return_tensors="pt")["input_ids"][0].tolist()
        out.append({"input_ids": ids[-max_len:], "labels": [-100] * len(ids[-max_len:])})
    return out


def run(cfg: P2Config, out_dir: Path) -> dict:
    t_start = time.monotonic()
    timing: dict[str, float] = {}
    torch.manual_seed(cfg.seed)
    out_dir.mkdir(parents=True, exist_ok=True)
    work = out_dir / "work"
    if work.exists():  # slot DB persists across runs; always start fresh
        import shutil

        shutil.rmtree(work)
    work.mkdir(parents=True)

    # ---------------------------------------------------------- 1. warmup
    t0 = time.monotonic()
    world = SyntheticWorld(default_config(seed=cfg.seed)).run(
        cfg.warmup_episodes, cfg.warmup_per_episode
    )
    cache = EmbeddingCache(Embedder()._encode)  # dedup around the real encoder
    embedder = Embedder(encode_fn=cache)
    service = SlotService(work / "p2_slots.db")
    memory = SemanticMemory(service, embedder)
    # Batch pre-encode everything the warmup touches; per-case cache misses
    # would cost one encoder forward each (the smoke run's dominant cost).
    cb = world.casebook
    n_cases = len(cb.case_ids)
    canon_texts = [canonicalize(case_row_from_casebook(cb, i)) for i in range(n_cases)]
    exp_texts = [
        experience_text(
            case_row_from_casebook(cb, i),
            "bad" if int(world.ledger.outcome[i]) == 1 else "good",
        )
        for i in range(n_cases)
    ]
    embedder.embed_keys(canon_texts)
    embedder.embed_values(exp_texts)
    warmup_memory(memory, world)  # canonical writes + outcome reflow
    store = service.store
    n_slots = len(store.all_slots())
    reps = [s.reputation for s in store.all_slots()]
    timing["warmup_sec"] = time.monotonic() - t0
    print(f"[warmup] {len(world.casebook.case_ids)} cases -> {n_slots} slots, "
          f"rep range [{min(reps):.2f}, {max(reps):.2f}] "
          f"({timing['warmup_sec']:.1f}s)")

    # ------------------------------------------------- 2. distill + replay
    cb = world.casebook
    last_ep = cfg.warmup_episodes - 1
    distill_idx = np.where(cb.episode < last_ep)[0]
    replay_idx = np.where(cb.episode == last_ep)[0]
    old_idx = np.where(cb.episode == 0)[0]
    distill_cases = _case_records(world, distill_idx)
    pairs = build_distill_set(
        store.all_slots(), distill_cases,
        config=DistillConfig(max_pairs=cfg.distill_max_pairs),
    )
    replay_pairs = build_distill_set([], _case_records(world, replay_idx))[
        : cfg.replay_pairs
    ]
    old_pairs = build_distill_set([], _case_records(world, old_idx))[
        : cfg.old_probe_pairs
    ]
    n_case_pairs = sum(1 for p in pairs if p.source == "case")
    print(f"[distill] {len(pairs)} pairs ({n_case_pairs} case / "
          f"{len(pairs) - n_case_pairs} slot); replay {len(replay_pairs)}, "
          f"old probe {len(old_pairs)}")

    # ------------------------------------------------------- 3. model weld
    t0 = time.monotonic()
    llm = LocalLLM(cfg.model_id, device="cpu").load()
    base = llm.model
    tokenizer = llm.tokenizer
    reader = StagedMemoryReader()
    injection = MemoryInjection(
        base, reader, layer_idx=cfg.layer_idx, top_k=cfg.top_k, query_dim=256
    )
    timing["model_load_sec"] = time.monotonic() - t0

    # Gate probe inputs: recent-case prompts, staged related/unrelated hits.
    probe_records = _case_records(world, replay_idx[: cfg.probe_samples])
    probe_texts = [r.summary for r in probe_records]
    probe_prompts = [
        p.prompt for p in build_distill_set([], probe_records)
    ]
    probe_encoded = _encode_prompts(tokenizer, probe_prompts, cfg.max_len)
    related_hits, unrelated_hits = build_probe_sets(
        embedder, store, probe_texts, cfg.top_k
    )

    def _probe(model):
        rel, rel_hits = run_gate_probe(
            model, injection, reader, probe_encoded, related_hits, cfg.batch_size
        )
        unrel, unrel_hits = run_gate_probe(
            model, injection, reader, probe_encoded, unrelated_hits, cfg.batch_size
        )
        return rel, rel_hits, unrel, unrel_hits

    pre_rel, _h1, pre_unrel, _h2 = _probe(base)
    print(f"[probe:pre-train] related mean {np.mean(pre_rel):.3f} / "
          f"unrelated mean {np.mean(pre_unrel):.3f} (zero-init -> ~0.5)")

    # ------------------------------------------------- 4. joint training
    t0 = time.monotonic()
    snap = snapshot_base_params(base)
    from peft import LoraConfig, get_peft_model

    peft_model = get_peft_model(
        base,
        LoraConfig(
            r=cfg.lora_r,
            lora_alpha=cfg.lora_alpha,
            lora_dropout=cfg.lora_dropout,
            target_modules=list(cfg.lora_target_modules),
            task_type="CAUSAL_LM",
        ),
    )
    peft_model.train()
    opt = build_joint_optimizer(peft_model, injection, cfg.lora_lr, cfg.injection_lr)
    # Phase 1 (freeze_lora_steps): the LoRA group's lr is held at 0 so only
    # the injection path (mem_out + gate) can fit the completions — memory
    # must become useful BEFORE the LM channel is allowed to memorize.
    opt.param_groups[0]["lr"] = 0.0

    # Encode + stage: case pairs get memory keyed by their own prompt text;
    # slot pairs run as memory misses (no case context to retrieve with).
    # Prompt keys are batch-encoded once (single-text calls would each cost
    # a full encoder forward).
    case_prompts = sorted({p.prompt for p in pairs if p.source == "case"})
    embedder.embed_keys(case_prompts + list(OFF_DOMAIN_TEXTS))
    staged_cache = {
        prompt: hits_for_key(store, embedder.embed_keys([prompt])[0], cfg.top_k)
        for prompt in case_prompts
    }
    off_hits = [
        hits_for_key(store, embedder.embed_keys([text])[0], cfg.top_k)
        for text in OFF_DOMAIN_TEXTS
    ]
    mismatch_every = round(1.0 / cfg.mismatch_fraction) if cfg.mismatch_fraction else 0
    examples = []
    n_mismatched = 0
    for j, pair in enumerate(pairs):
        enc = encode_pair(tokenizer, pair.prompt, pair.completion, cfg.max_len)
        staged = None
        if pair.source == "case":
            if mismatch_every and j % mismatch_every == mismatch_every - 1:
                staged = off_hits[j % len(off_hits)]  # contrastive: close me
                n_mismatched += 1
            else:
                staged = staged_cache[pair.prompt]
        examples.append({**enc, "staged": staged})

    rng = np.random.default_rng(cfg.seed)
    chunks = length_grouped_batches(examples, cfg.batch_size, rng)
    losses: list[float] = []
    step_times: list[float] = []
    steps = 0
    mid_rel: list[float] = []
    mid_unrel: list[float] = []
    while steps < cfg.max_steps:
        for chunk in chunks:
            if steps >= cfg.max_steps:
                break
            if steps == cfg.freeze_lora_steps:
                # Phase boundary: probe the injection-only gate, then unfreeze LoRA.
                peft_model.eval()
                mid_rel, _h1, mid_unrel, _h2 = _probe(peft_model)
                peft_model.train()
                opt.param_groups[0]["lr"] = cfg.lora_lr
                print(f"[probe:phase-1] related mean {np.mean(mid_rel):.3f} / "
                      f"unrelated mean {np.mean(mid_unrel):.3f} "
                      f"(injection-only; LoRA now unfreezing)")
            reader.stage([e["staged"] for e in chunk])
            batch = collate_uniform(chunk)
            ts = time.monotonic()
            loss = peft_model(**batch).loss
            loss.backward()
            opt.step()
            opt.zero_grad()
            step_times.append(time.monotonic() - ts)
            losses.append(float(loss.detach()))
            steps += 1
            if steps == 1:
                print(f"[train] first step {step_times[0]:.2f}s "
                      f"(batch {cfg.batch_size}, len {batch['input_ids'].shape[1]})")
        # fresh shuffle each epoch
        chunks = length_grouped_batches(examples, cfg.batch_size, rng)
    assert_base_unchanged(snap, base)  # runtime guard: only LoRA+injection moved
    timing["train_sec"] = time.monotonic() - t0
    gate_w = float(injection.gate.weight.detach().abs().sum())
    mem_w = float(injection.mem_out.weight.detach().abs().sum())
    print(f"[train] {steps} steps, loss {losses[0]:.3f} -> {losses[-1]:.3f}, "
          f"median step {float(np.median(step_times)):.2f}s, "
          f"|gate_w| {gate_w:.4f}, |mem_out_w| {mem_w:.1f} "
          f"({timing['train_sec']:.1f}s)")

    # Save the challenger adapter + the injection state (audit trail).
    adapter_dir = out_dir / "adapters" / datetime.now(timezone.utc).strftime("%Y%m%d")
    adapter_dir.mkdir(parents=True, exist_ok=True)
    peft_model.save_pretrained(str(adapter_dir))
    meta = {
        "created_at": datetime.now(timezone.utc).isoformat(),
        "n_examples": len(pairs),
        "steps": steps,
        "first_loss": losses[0],
        "final_loss": losses[-1],
        "median_step_sec": float(np.median(step_times)),
        "lora_lr": cfg.lora_lr,
        "injection_lr": cfg.injection_lr,
        "joint": True,
        "peft": {
            "r": cfg.lora_r,
            "lora_alpha": cfg.lora_alpha,
            "lora_dropout": cfg.lora_dropout,
            "target_modules": list(cfg.lora_target_modules),
        },
    }
    (adapter_dir / "meta.json").write_text(json.dumps(meta, indent=2, ensure_ascii=False))
    torch.save(
        {"mem_out": injection.mem_out.state_dict(), "gate": injection.gate.state_dict()},
        out_dir / "injection_after_train.pt",
    )

    # ------------------------------------------- 5. post-train gate probe
    peft_model.eval()
    post_rel, post_rel_hits, post_unrel, post_unrel_hits = _probe(peft_model)
    rel_mean = float(np.mean(post_rel))
    unrel_mean = float(np.mean(post_unrel))
    gate_pass = gate_verdict(rel_mean, unrel_mean)
    print(f"[probe:post-train] related mean {rel_mean:.3f} "
          f"(target > 0.7) / unrelated mean {unrel_mean:.3f} (target < 0.2) "
          f"-> {'PASS' if gate_pass else 'FAIL'}")

    # ---------------------------------------------------- 6. promotion gate
    t0 = time.monotonic()
    injection.detach()
    restored = peft_model.unload()  # pure base; the gate mounts per-eval
    gate = PromotionGate(restored, tokenizer, out_dir / "promotion_log.jsonl",
                         max_len=cfg.max_len)
    verdict = gate.judge(None, str(adapter_dir), replay_pairs, old_pairs=old_pairs)
    rel_imp = (verdict.replay_champion_nll - verdict.replay_challenger_nll) / max(
        verdict.replay_champion_nll, 1e-9
    )
    old_regr = (verdict.old_challenger_nll - verdict.old_champion_nll) / max(
        verdict.old_champion_nll, 1e-9
    )
    timing["promotion_sec"] = time.monotonic() - t0
    print(f"[promotion] replay NLL {verdict.replay_champion_nll:.4f} -> "
          f"{verdict.replay_challenger_nll:.4f} ({rel_imp:+.2%}); "
          f"old {verdict.old_champion_nll:.4f} -> "
          f"{verdict.old_challenger_nll:.4f} ({old_regr:+.2%}, NT<5%) "
          f"-> {'PROMOTED' if verdict.promoted else 'REJECTED'}: {verdict.reason}")

    # -------------------------------------------------------- 7. artifacts
    timing["total_sec"] = time.monotonic() - t_start
    service.persist()
    service.close()
    gate_stats = {
        "pre": {"related": summarize_gates(pre_rel),
                "unrelated": summarize_gates(pre_unrel)},
        "phase1": {"related": summarize_gates(mid_rel),
                   "unrelated": summarize_gates(mid_unrel)},
        "post": {"related": summarize_gates(post_rel),
                 "unrelated": summarize_gates(post_unrel)},
        "hits": {"related": post_rel_hits, "unrelated": post_unrel_hits},
        "gates": {"related": post_rel, "unrelated": post_unrel},
        "note": "query_proj is frozen-random (retrieval non-differentiable); "
                "probe hits are staged from case-text keys vs off-domain keys, "
                "so gate features (sum_w, max_w) carry the relatedness signal.",
    }
    training_stats = {
        "steps": steps,
        "first_loss": losses[0],
        "final_loss": losses[-1],
        "losses_tail": losses[-10:],
        "median_step_sec": float(np.median(step_times)),
        "n_pairs": len(pairs),
        "n_case_pairs": n_case_pairs,
        "n_mismatched": n_mismatched,
        "freeze_lora_steps": cfg.freeze_lora_steps,
        "slots": n_slots,
        "gate_weight_abs_sum": gate_w,
        "mem_out_weight_abs_sum": mem_w,
        "adapter_dir": str(adapter_dir),
    }
    promotion_stats = {
        "promoted": verdict.promoted,
        "reason": verdict.reason,
        "replay_champion_nll": verdict.replay_champion_nll,
        "replay_challenger_nll": verdict.replay_challenger_nll,
        "rel_improvement": rel_imp,
        "old_champion_nll": verdict.old_champion_nll,
        "old_challenger_nll": verdict.old_challenger_nll,
        "old_regression": old_regr,
        "replay_pairs": len(replay_pairs),
        "old_pairs": len(old_pairs),
    }
    metrics = build_p2_metrics(cfg, timing, training_stats, gate_stats, promotion_stats)
    write_metrics(metrics, out_dir / "p2_metrics.json")
    plot_gate_hist(post_rel, post_unrel, out_dir / "gate_alpha_hist.png",
                   pre_related=pre_rel, pre_unrelated=pre_unrel)
    return metrics


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="P2 acceptance experiment")
    parser.add_argument("--out", type=Path, default=Path("eval/artifacts-p2"))
    parser.add_argument("--seed", type=int, default=P2Config.seed)
    parser.add_argument("--steps", type=int, default=P2Config.max_steps)
    parser.add_argument("--max-len", type=int, default=P2Config.max_len)
    parser.add_argument("--batch-size", type=int, default=P2Config.batch_size)
    parser.add_argument("--lora-lr", type=float, default=P2Config.lora_lr)
    parser.add_argument("--injection-lr", type=float, default=P2Config.injection_lr)
    parser.add_argument("--quick", action="store_true",
                        help="tiny smoke run (12 steps, 60 pairs, 8 probe samples)")
    args = parser.parse_args(argv)

    cfg = P2Config(seed=args.seed, max_steps=args.steps, max_len=args.max_len,
                   batch_size=args.batch_size, lora_lr=args.lora_lr,
                   injection_lr=args.injection_lr)
    if args.quick:
        cfg = P2Config(seed=args.seed, max_steps=args.steps,
                       lora_lr=args.lora_lr, injection_lr=args.injection_lr,
                       max_len=args.max_len, batch_size=args.batch_size,
                       distill_max_pairs=60, freeze_lora_steps=4,
                       probe_samples=8, replay_pairs=16, old_probe_pairs=8,
                       warmup_episodes=3, warmup_per_episode=40)

    metrics = run(cfg, args.out)
    v = metrics["verdicts"]
    print(f"VERDICT gate_alpha: {v['gate_alpha']} | "
          f"lora_promotion: {v['lora_promotion']} | overall: {v['overall']} "
          f"(artifacts in {args.out}, total {metrics['timing']['total_sec']:.1f}s)")
    return 0 if v["overall"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
