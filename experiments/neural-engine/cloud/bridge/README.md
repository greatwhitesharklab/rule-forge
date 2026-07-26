# Agent Bridge Protocol

This directory is the file-system rendezvous between the local RuleForge
self-learning system and an AI agent (e.g. Kimi CLI) acting as the cloud
executor. No HTTP, no keys — the agent *is* the cloud.

## Directories

- `outbox/` — sanitized task packages written by the local system (one JSON per task)
- `inbox/` — result files written by the agent (one JSON per task)
- `archive/` — consumed inbox files are moved here; never write here yourself

Runtime contents of all three are git-ignored (see `.gitignore` here).

## Outbox file: `outbox/{task_id}.json`

Already PII-sanitized (names/phones/IDs/amounts/dates are redacted or
generalized — treat everything in it as safe to read, and never try to
re-identify):

```json
{
  "task_id": "demo-20260726-120000",
  "task_type": "feature_proposal",
  "timestamp": "2026-07-26T04:00:00+00:00",
  "constraints": {"max_features": 5, "must_be_executable": "python", "no_future_info": true},
  "output_schema": {"features": [{"name": "str", "expression": "str", "rationale": "str"}]},
  "prompt": "{\"task\": \"feature_proposal\", ...}"
}
```

`prompt` is the full task as a JSON string (task type, context, constraints,
output schema). `output_schema` describes the shape your result must have.

## What the agent must do

1. Read `outbox/{task_id}.json`, parse `prompt`.
2. Produce a result whose top-level object matches the task's output schema
   (for `feature_proposal`: `{"features": [{"name", "expression", "rationale"}, ...]}`;
   `expression` must be an executable Python expression over case fields,
   using only data available at decision time — `no_future_info`).
3. Write `inbox/{task_id}.json` (same `{task_id}` as the outbox file):

```json
{
  "model": "kimi-k2",
  "model_version": "2026-07-26",
  "content": {"features": [{"name": "flow_volatility_30d", "expression": "flow_std_30d / flow_mean_30d", "rationale": "unstable cash flow predicts default"}]},
  "usage": {"input_tokens": 0, "output_tokens": 0}
}
```

- `model` / `model_version`: declare yourself honestly — they land in the
  provenance ledger so quality regressions can be attributed to "the cloud
  changed" vs "local was wrong". Both default to `"unknown"` if omitted.
- `content`: the result object (must be a JSON object, validated locally
  against the task contract; invalid results are rejected and logged).
- `usage`: optional token accounting; omit if unknown.

## Lifecycle notes

- The local system polls the inbox with a timeout (default 600s). Write the
  file atomically (write to a temp name, then rename) if you produce it
  incrementally — partially written JSON is ignored until it parses.
- Once read, your inbox file is moved to `archive/`. A reply is consumed
  exactly once; a repeated task id needs a fresh inbox file.
- The bridge only ever sees sanitized data. Do not attempt to reconstruct or
  request real identities — the outbound gate blocks PII by design.
