# V5.100.8 — `evaluationRete` label84/label82 labeled loop flatten (TD-19.5.7)

## Context

V5.96 skip 最后收口 — core 内最后一个 decompiled labeled loop。
`KnowledgeSessionImpl.evaluationRete:308-393` 是 **真 per-fact hot path** (THE rete evaluation
loop, 每次 fireRules 跑)。 3-level nested labeled loop:
- **outer label84**: 遍历 reteInstanceList, skip null-activation-group 的 reteInstance
- **middle label82**: 遍历 activation-group keys, skip 已 actived 的 key
- **innermost**: 遍历 group 内 units, skip not-effective/expired, 找首个产生 trackers 的 unit

2 处 cross-loop continue (`continue label84` = 下一个 reteInstance; `continue label82` = 下一个 key)。
V5.96 显式 skip (runtime hot path + ACTIVE label, 需大 characterization test 投资)。 V5.100.8
收口, 套 V6.3/V6.4 + V5.100.5/V5.100.7 模式。

### 关键等价性 (cross-loop continue → enhanced-for 自然流):

- `continue label84` (middle keys 耗尽, 跳到下一个 reteInstance) → middle for 自然结束后, outer
  for 走下一次 iteration。 等价。
- `continue label82` (innermost units 耗尽, 跳到下一个 key) → innermost for 自然结束后, middle
  for 走下一次 iteration。 等价。
- 内层 find-valid (do-while skip not-effective + skip expired) → 2 continue。
- middle find-next-not-actived (do-while contains-check) → contains-check + continue。
- outer find-next-non-null-activation-map (do-while reteInstanceMap==null) → null-check + continue。
- `trackers` 变量保持 reteInstance scope (跟原 label84 body 内 `Collection trackers` 一致)。
- 原顶部 `!hasNext → clean() + return` → for 耗尽后 `clean()` (等价 — 两者都只在全部 reteInstance
  处理完后到达)。

## 改动

### 文件 1: `KnowledgeSessionImpl.java` (evaluationRete 3-level labeled loop → 3 enhanced for)

- `label84 + while(true) { do {...} while(map==null); label82... }` → `for (ReteInstance : list) { ... if(map==null) continue; for(key) { for(unit) {...} } } clean()`
- 砍 `Iterator reteInstanceIterator` / `var7` / `var11` (3 个 raw Iterator)
- 保留全部 V5.83 sticky-state 注释 + doRete 调用 + agenda/activation putAll

### 文件 2: 无新 test (路径已有 dedicated characterization)

evaluationRete 的 activation-group 路径由 **V5.48-era 5 个 dedicated test** 锁定:
- `KnowledgeSessionTest.EffectiveDateWindow` (3 tests): effectiveDate 未来→不fire / 过去→fire /
  null→fire (锁 innermost isNotYetEffective + valid)
- `KnowledgeSessionTest.ExpiresDateWindow` (2 tests): expiresDate 过去→不fire (锁 innermost isExpired)

这 5 个 test post-flatten 全 pass = 行为等价证明。 跟 V5.100.5 (AndBuilder, 0 coverage 需新 test)
/ V5.100.7 (active* loop body, 无 real-unit coverage 需新 test) 不同 — evaluationRette 路径**已有**
dedicated coverage, 加 redundant test 是 ceremony 非 value。

## Verification

### Step 1 — 全量回归

```bash
mvn test -pl lib/ruleforge-core
```

- 全量: **783/783 pass**, 零 regression
- 含 EffectiveDateWindow (3) + ExpiresDateWindow (2) activation-group 路径全 pass = flatten 行为等价

### Step 2 — Perf bench (hot path 必须 neutral)

```bash
mvn test -pl lib/ruleforge-core -Dtest=HotPathBenchTest,EvalBenchmarkV579
```

- `[V5.87 HotPathBench] per-fact=0.10us` (跟 V6.1/V5.100.5 baseline 0.09-0.10us range overlap, **neutral**)
- `[V5.79 EvalBenchmark] no_eval fired=3 / no_eval_5r fired=5 / no_eval_3way fired=0 / eval fired=0`
  (匹配 V5.83 baseline fired counts, **neutral**)

**结论**: flatten 是 pure control-flow refactor, 同 operation 不同控制流, wall-time neutral。

## 复用现有 utility / 模式

- 完全沿 V6.3 KnowledgeBase + V6.4 LeftParser + V5.100.5 AndBuilder + V5.100.7 active* 的
  "do-while-find-X → enhanced for + continue" 模式 (本个是 3-level + cross-loop continue 变种)
- cross-loop continue (`continue labelNN`) → enhanced-for 自然流 (下一层 iter) 是 V5.100.8 立的
  关键洞察 — labeled continue 在 enhanced for 里等价 "走到下一层 iter"
- 0 新工具, 0 新 API

## Skip 维持

- `RulesRebuilder.java:614/633` — null-value 风险, V5.93 原则不适用
- `EngineContext.java:44` — duplicate detection (非 first-wins)
- `putKnowledgeSession:518` — session 参数 null-value 风险

## 风险 / 已知 trade-off

1. **cross-loop continue 等价性**: `continue label84/label82` → enhanced-for 下一层 iter, 3 处全等价
   (783 regression + 5 activation-group dedicated test 全 pass 显式 lock)。
2. **trackers 变量 scope**: 保持 reteInstance scope (跟原一致), 跨 key/unit 复用, 行为 100% 一致。
3. **empty-facts edge case**: 原 + flatten 都有 "facts 空 → trackers stale" 的潜在行为 (facts 实际
   永非空, fireRules 传 allFactsList, evaluation 传 [obj]), 保留不修。
4. **hot path perf**: per-fact 0.10us neutral (HotPathBench 验证), 同 operation 不同控制流。
5. **`!hasNext → clean() + return` 时机**: 原 do-while 顶部 → for 耗尽后, 两者都只在全部 reteInstance
   处理完到达, 等价。
6. **dedicated characterization 已存在**: EffectiveDateWindow + ExpiresDateWindow 5 test 锁路径,
   不需加 redundant test (跟 V5.100.5/7 加 test 的不同理由)。

## 引用

- [[v596-var123-cleanup]] V5.96 立的 skip (KnowledgeSessionImpl labeled loops, 最后收口)
- [[project-v633-knowledgebase-dowhile-flatten]] [[project-v644-leftparser-commonfunction-flatten]]
  V6.3/V6.4 模式
- [[project-v51005-andbuilder-buildcriterion-outer-flatten]] V5.100.5 find-non-null 模式
- [[project-v51007-knowledgesessionimpl-active-loop-flatten]] V5.100.7 同 class labeled loop flatten
- [[feedback-version-x999-xcap]] V5.100.8 = V5.100 第八个 Fix (Fix 位 = 8), V5.96 skip 完整收口
- **V5.96 skip 完整收口**: core 内所有 decompiled labeled loop (evaluationRette label84/82 + active*
  label42/while-true-do-while + AndBuilder outer) 全部 flatten 完。 仅剩 null-value 风险 /
  duplicate-detection 的 containsKey (不动)。
