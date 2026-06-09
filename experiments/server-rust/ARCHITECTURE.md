# Architecture — Java ↔ Rust decision flow executor

Comparison of the **6 key design points** that drive the V5.20+ Java
self-built engine (`ruleforge-decision`) and the Phase 1-7 Rust port
(`server-rust`, previously called `rust-ruleforge`). The goal is **architectural exploration**, not 1:1
translation — Rust picks the type-system-driven shape that fits
naturally, Java sticks with what the Spring / Flowable heritage gives
it.

| # | Concern | Java (V5.20+) | Rust (Phase 1-7) |
|---|---|---|---|
| 1 | Suspend / fail control flow | `AsyncNodeSuspendException extends RuntimeException` | `enum NodeResult { Continue, Suspend(SuspendInfo), Branch(String) }` |
| 2 | Node dispatch | `NodeExecutorRegistry` keyed by `node.getType()` string + `taskType` string | Sum-type `enum NodeKind` + `match` (exhaustive at compile time) |
| 3 | Flow state machine | `DecisionFlowState.status: String` with `@Scheduled` recovery job checking the value at runtime | `enum FlowStatus { Pending, Running, PendingAsync, WaitingCallback, Completed, Failed }` + sqlx column `CHECK` |
| 4 | Variable bag | `Map<String, Object>` (any type) | `Vars(BTreeMap<String, serde_json::Value>)` (only JSON values) |
| 5 | State persistence | MySQL `nd_decision_flow_state` with `locked_until <= NOW()` timestamp CAS | Postgres `rust_decision_flow_state` with `pg_try_advisory_lock(hashtext(flow_run_id))` + `FOR UPDATE SKIP LOCKED` |
| 6 | XML parsing | `org.dom4j` (~5000 String allocations per 50KB BPMN) | `roxmltree` 0.20 (zero-copy, `&str` borrows from input) |

## 1. Suspend / fail control flow — exception vs sealed enum

**Java** expresses "this node is waiting on external input" by throwing
an exception that the traverser catches:

```java
// UserTaskNodeExecutor.java (V5.18+)
String field = node.attr("ruleforge:decisionField");
ctx.setCurrentAwaitingField(field);
throw new AsyncNodeSuspendException(
    "USER_TASK", field, /* nextRetryAt */ null, payload);
```

The traverser unwraps it:

```java
try {
    outcome = executor.execute(node, ctx);
} catch (AsyncNodeSuspendException ex) {
    return onSuspend(ex);
}
```

The `throw` is semantically a **return** — but encoded as control
flow, so any non-`AsyncNodeSuspendException` (a real bug, NPE,
deserialise error) takes the same path until someone reads the type
carefully.

**Rust** encodes it as data:

```rust
pub enum NodeResult {
    Continue,                            // step done, follow next_node
    Branch(String),                      // explicit target (Action)
    Suspend(SuspendInfo),                // waiting on external input
}

pub struct SuspendInfo {
    pub wait_type: WaitType,             // UserTask | AsyncData | AsyncTask
    pub wait_ref: String,                // node id / callback id
    pub next_retry_at: Option<Instant>,
    pub payload: serde_json::Value,      // what the frontend sees
}
```

The traverser:

```rust
match executor.execute(node, &mut ctx).await? {
    NodeResult::Continue => { /* follow next_node */ }
    NodeResult::Branch(target) => { next_id = target; }
    NodeResult::Suspend(info) => return Ok(TraversalOutcome::Suspended(info)),
}
```

The two failure paths are cleanly separated: `?` returns `Err(FlowError)`
(a real failure), `Suspend` returns a value (a normal outcome). No
exception, no implicit `instanceof` filter.

## 2. Node dispatch — string-keyed registry vs sum-type `match`

**Java** has 9 `NodeExecutor` beans, each implementing
`supportedType(): String`. The registry looks them up at runtime:

```java
// NodeExecutorRegistry.java
public NodeExecutor get(Node node) {
    String key = node.getType() + ":" + node.getTaskType();
    return executors.get(key);  // HashMap lookup; miss → NPE
}
```

Adding a new node type means (a) implementing `NodeExecutor`, (b)
registering it in `NodeExecutorRegistry` as a Spring bean, (c)
updating the key string. Miss the registration → NPE at runtime.

**Rust** has the sum type:

```rust
pub enum NodeKind {
    StartEvent,
    EndEvent,
    ServiceTask { task_type: TaskType, attrs: Attrs },
    ScriptTask { format: String, source: String, attrs: Attrs },
    UserTask { decision_type: String, decision_field: String, attrs: Attrs },
    ExclusiveGateway { attrs: Attrs },
    ParallelGateway { attrs: Attrs },
    IntermediateEvent { attrs: Attrs },
    SubProcess,  // unsupported — would construct-time error
}
```

Dispatch is a `match`:

```rust
match &node.kind {
    NodeKind::ServiceTask { task_type: TaskType::Rule, .. } =>
        reg.rule.execute(node, ctx).await,
    NodeKind::ServiceTask { task_type: TaskType::Action, .. } =>
        reg.action.execute(node, ctx).await,
    NodeKind::ScriptTask { .. } => reg.script.execute(node, ctx).await,
    NodeKind::UserTask { .. } => reg.user_task.execute(node, ctx).await,
    NodeKind::ExclusiveGateway { .. } => reg.gateway.execute(node, ctx).await,
    // ... exhaustive; missing arm = `non-exhaustive patterns` compile error
    NodeKind::SubProcess => Err(FlowError::Unsupported("SubProcess")),
}
```

Adding a new node means adding a `match` arm. Forget it → compile
error, not NPE.

## 3. State machine — `String` status vs `enum FlowStatus` + sqlx `CHECK`

**Java** has a `DecisionFlowState.status: String` column and an
`@Scheduled` job that reads it back:

```java
// DecisionFlowStateService.java
if (STATUS_COMPLETED.equals(state.getStatus())) {
    return;  // don't re-drive
}
// ... ad-hoc string comparisons everywhere
```

Typo (`"WAITTING_CALLBACK"`) compiles. Java's only safety net is
unit tests.

**Rust** has the enum + sqlx `Encode`/`Decode` + column `CHECK`:

```sql
status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
  CHECK (status IN ('PENDING','RUNNING','PENDING_ASYNC',
                    'WAITING_CALLBACK','COMPLETED','FAILED'))
```

```rust
pub enum FlowStatus {
    Pending, Running, PendingAsync,
    WaitingCallback, Completed, Failed,
}
```

`FlowStatus` is the only way to write the column. `Status != FlowStatus::Completed` is
type-checked; an unknown string at decode time is a sqlx error
("unknown FlowStatus: WAITTING_CALLBACK").

## 4. Variable bag — `Map<String, Object>` vs `Vars(BTreeMap<String, Value>)`

**Java** has `FlowContext.vars: Map<String, Object>`. Any type can be
stuffed in. The `BeanUtils.populate` V5.18 bug was a symptom:
`outputModel` was a Java POJO that flowed back into the rule engine
as `Map<String, Object>` of bean properties, and the rule engine
expected a different shape.

**Rust** locks the type to JSON:

```rust
pub struct Vars(pub BTreeMap<String, serde_json::Value>);

impl Vars {
    pub fn get_str(&self, k: &str) -> Option<&str> { ... }
    pub fn get_i64(&self, k: &str) -> Option<i64> { ... }
    pub fn get_bool(&self, k: &str) -> Option<bool> { ... }
    pub fn resolve_path(&self, path: &str) -> Option<&Value> { ... }
}
```

The `Value` is JSON (String, Number, Bool, Array, Object, Null). No
arbitrary struct can sneak in — the type system says no. `resolve_path`
gives the dot-path access that Java's `ConditionEvaluator` needs.

BTreeMap (not HashMap) so `Vars: PartialEq` — useful for snapshot
tests and for the persistence layer's row_vars round-trip.

## 5. State persistence — MySQL timestamp CAS vs Postgres `pg_try_advisory_lock`

**Java** writes to MySQL `nd_decision_flow_state` and uses a
**timestamp-based CAS**:

```java
// DecisionFlowStateService.tryLock
if (state.getLockedUntil() == null
    || state.getLockedUntil().before(new Date())) {
    state.setLockedBy(workerId);
    state.setLockedUntil(new Date(now + LOCK_TTL));
    return true;
}
return false;
```

Two workers can read `locked_until = null`, both think they have it,
both write. Clock skew, GC pause, or a slow UPDATE can cause double
execution. The recovery job compensates by re-running idempotently,
but it's a real cost.

**Rust** uses Postgres's **real** lock primitive:

```sql
SELECT pg_try_advisory_lock(hashtext($1))  -- returns true/false
SELECT pg_advisory_unlock(hashtext($1))
```

`hashtext(flow_run_id)` is a `bigint`, which is the advisory-lock key
domain. Exactly one caller wins, regardless of clock skew or process
count. The recovery sweep uses a different lock domain:

```sql
SELECT ... FROM rust_decision_flow_state
WHERE status IN ('PENDING_ASYNC','WAITING_CALLBACK')
  AND (next_retry_at IS NULL OR next_retry_at <= NOW())
ORDER BY id LIMIT 20
FOR UPDATE SKIP LOCKED;
```

`FOR UPDATE SKIP LOCKED` is the standard Postgres "task queue" idiom —
multiple workers can sweep concurrently, each picks rows the others
haven't. Then per-row `pg_try_advisory_lock(id)` for the long-running
resume (the row-lock is released as soon as the sweep transaction
commits, so it doesn't gate anything except the sweep itself).

Other pg-vs-mysql differences:
- `JSONB` over `MEDIUMTEXT` — `row_vars->'applicant'->'age'` is a
  queryable expression; MEDIUMTEXT is opaque.
- `TIMESTAMPTZ` over `DATETIME` — no tz bugs.
- `ON CONFLICT (flow_run_id) DO UPDATE` over `ON DUPLICATE KEY UPDATE`.

## 6. XML parsing — `org.dom4j` vs `roxmltree`

**Java** uses dom4j, which builds a tree of `Element` nodes with
`String` attributes. For a 50KB BPMN, that's ~5000 `String`
allocations on parse.

**Rust** uses `roxmltree`, which builds an immutable tree where every
attribute value is a `&str` borrowing from the input string. ~0
allocations on parse; lookup is a `HashMap`-style descendant search
over the same buffer.

```rust
// rf-parse::BpmnXmlParser
let doc = roxmltree::Document::parse(xml)?;     // no allocation
let process = doc.root_element()
    .descendants().find(|n| n.has_tag_name("process"))?;
let id = process.attribute("id").ok_or(...)?;    // &str borrow
```

Trade-offs:
- `roxmltree` is read-only (we don't need write).
- No XPath (we walk manually — same iteration pattern as Java's
  `BpmnXmlParser.java` line 73-78).
- `&str` lifetime ties the parsed IR to the input string. The HTTP
  layer holds the `FlowDefinition` (with the source XML) in an
  `Arc`, so lifetimes are stable for the cache's lifetime.

## What Java wins at

- Spring's DI + the existing `@Scheduled` infrastructure made
  `DecisionFlowRecoveryJob` a one-class addition. Rust has to wire
  its own `tokio::spawn` + `tokio::time::interval` (`RecoveryLoop` in
  `rf-state/src/recovery.rs`).
- IDEs (IntelliJ) navigate the Java executor graph with click-through
  — Rust's `match` exhaustiveness is checked but the call graph is
  harder to inspect without `rust-analyzer`.
- The `BeanUtils.populate` style "magic populator" lets the rule
  engine pull structured fields out of arbitrary POJOs. The Rust
  `Vars(Value)` model would require explicit `insert_serialized`
  calls — but the V5.18 fix already proved the magic populator is a
  source of bugs.

## What Rust wins at

- Compile-time exhaustiveness on `match` and type-state patterns.
- Real lock primitives (no clock-skew race in the recovery path).
- Single source of truth for the BPMN shape (`NodeKind` enum) — the
  parser, the executor, and the tests can't drift.
- Lower memory per flow (no intermediate `String` allocations in the
  parser; no `outputModel` POJO allocation in the rule engine path).

## What we don't do

- ❌ Replace the Java executor. The Rust port is sibling, not fork.
  Java continues to handle production traffic on 8280.
- ❌ Wire a real `RuleEngine` — only `MockRuleEngine` is in the tree.
- ❌ Implement all 9 Java `NodeExecutor`s. Rust has 5 + 3 stubs.
- ❌ Add K8s / Helm / production deployment manifests.
- ❌ Audit deps for `RUSTSEC` advisories (the Rust project is a
  learning artifact, not a deployable).

## Open questions

- The `decision` route writes the user-supplied decision into
  `vars[decision_field]` and also sets `current_awaiting_value`. The
  recovery path re-derives `current_awaiting_field` from
  `output_model.wait_ref` but doesn't restore `current_awaiting_value`
  (it's not in a column). A future column for it would make the
  recovery path 1:1 with the `/decision` path.
- The `flow_xml_version` is a SHA-256 of the BPMN XML, written to
  the row at suspend time. If the console saves a BPMN with byte-
  identical XML, the version matches and we proceed (correct). If
  the BPMN differs, we mark FAILED — but the user might want a
  "version mismatch: leave in PENDING_ASYNC" path for retry.
