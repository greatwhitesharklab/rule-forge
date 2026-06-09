# server-rust (rust-ruleforge)

Rust port of the RuleForge decision flow executor. Sibling project to the Java executor — runs on its own port (8281), uses its own Postgres state store, talks the same HTTP protocol to the Java console.

The whole point is **architectural exploration + comparison**, not 1:1 translation. See [`ARCHITECTURE.md`](ARCHITECTURE.md) for the Java ↔ Rust design comparison.

## Status

✅ Phase 1-7 done. Working: 5 NodeExecutors, pg-backed state, recovery loop, all 5 routes. 82 tests pass.

What's **not** here:
- No production deployment (the `postgres-rust` compose service is dev-only).
- Java console ↔ Rust notification chain (the console still broadcasts to Java on 8280; for Rust, `POST /ruleforge/flow/invalidate` is exposed but the console doesn't auto-call it).
- The Java 9 `NodeExecutor` set only has 5 in Rust (`Rule`, `Action`, `Script`, `Gateway`, `UserTask`); `ParallelGateway` / `IntermediateEvent` are stubs and `Package` / `RulesPackage` raise `Unsupported`.
- The Rust `RuleEngine` is `MockRuleEngine` only — wire a real one in `main.rs::MockRuleEngine` site.

## Crate layout

```
crates/
├── rf-ir/         # Immutable IR (FlowDefinition, FlowNode, NodeKind sum type)
├── rf-parse/      # BpmnXmlParser (roxmltree, zero-copy)
├── rf-executor/   # FlowContext, Traverser type-state, 5 NodeExecutors
├── rf-rule/       # trait RuleEngine + MockRuleEngine
├── rf-state/      # sqlx pg persistence + 30s RecoveryLoop
└── rf-http/       # axum service: /ruleforge/evaluate + /flow/decision + /flow/invalidate
```

Dependency chain (no cycles):
```
core ← rule ← http
core ← executor ← http
core ← executor ← state ← http
```

## Quick start

```bash
# Build
cargo build --workspace

# Test (in-memory suite only)
cargo test --workspace

# Test (in-memory + pg suite)
docker run -d --name ruleforge-pg-rust \
  -e POSTGRES_USER=ruleforge -e POSTGRES_PASSWORD=ruleforge \
  -e POSTGRES_DB=ruleforge_rust -p 5433:5432 postgres:16-alpine
PG_URL=postgres://ruleforge:ruleforge@127.0.0.1:5433/ruleforge_rust \
  cargo test --workspace

# Lint + format
cargo clippy --workspace --all-targets -- -D warnings
cargo fmt --check

# Run
CONSOLE_URL=http://localhost:8180 \
PG_URL=postgres://ruleforge:ruleforge@127.0.0.1:5433/ruleforge_rust \
HTTP_PORT=8281 \
cargo run --bin rf-http

# Smoke
curl -X POST localhost:8281/ruleforge/evaluate \
  -H 'content-type: application/json' \
  -d '{"flow_id": "test", "vars": {}}'
```

Drop `PG_URL` to use the in-memory inflight store (state lost on restart — fine for dev).

## Routes (Phase 6 surface)

| Path | Method | Purpose |
|---|---|---|
| `/health` | GET | `{"status": "ok", "phase": 6, "worker_id", "cache_size", "inflight_count"}` |
| `/ruleforge/evaluate` | POST | Run a flow. Body: `{"flow_id", "vars", "applicant?", "order?", "user_id?", "order_no?"}`. Returns `{"result": "COMPLETED", "flow_run_id", "vars", "current_node_id"}` or `{"result": "PENDING", "flow_run_id", "wait_ref", "payload"}` |
| `/ruleforge/flow/decision` | POST | Resume a USER_TASK. Query: `?flowRunId=...&decision=...` |
| `/ruleforge/flow/invalidate` | POST | Drop a flow from the cache. Query: `?flowId=...`. Returns `{"result": true/false, "flowId": "..."}` |
| `/ruleforge/flow/load` | GET | Proxy to Java console. Query: `?file=...` |

URL params keep the Java `flowRunId` / `flowId` camelCase so the Rust service is drop-in compatible with the console's existing callers.

## State store

`rust_decision_flow_state` table in Postgres (separate from the Java `nd_decision_flow_state` in MySQL — the two run side by side, each writing its own table).

- **Lock primitive**: `pg_try_advisory_lock(hashtext(flow_run_id))` (single-key CAS) + `FOR UPDATE SKIP LOCKED` (recovery sweep). Java uses timestamp CAS; pg gives us a real lock.
- **JSON**: `jsonb` over MySQL `MEDIUMTEXT` — queryable / indexable.
- **Timezone**: `TIMESTAMPTZ` over `DATETIME`.
- **Recovery sweep**: 60s initial delay, then 30s `tokio::time::interval`, mirrors the Java `@Scheduled(fixedRate=30_000, initialDelay=60_000)`.

Migration runs at startup via `sqlx::migrate!()` — no separate migrate tool.

## License

MIT
