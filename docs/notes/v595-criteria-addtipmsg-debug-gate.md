# V5.95 — `Criteria.evaluate` 4 `addTipMsg` + `cleanTipMsg` debug 门控

> **TL;DR**:`Criteria.evaluate` 4 个 `addTipMsg` 调用(line 34, 38, 115, 132)
> 即使 `Rule.debug = false` 仍无条件执行 — 4 string concat + 4 StringBuilder.append
> + 末尾 `cleanTipMsg()` 立即抹掉 builder 全部内容。V5.95 加 `Criteria.debug` 字段,
> evaluate 走 `if (this.debug)` 门控 4 addTipMsg(`cleanTipMsg` 保留调用契约,builder
> reset 仍要执行)。
>
> **Wall-time 验证(V5.94 → V5.95)**:
> - `HotPathBenchTest` per-fact **0.22us → 0.12us (-45%)** ✅✅
> - `EvalBenchmarkV579` 4 scenarios:
>   - `no_eval_5r`: 2.50ms → **1.43ms (-43%)** ✅
>   - `no_eval_3way`: 0.83ms → **0.68ms (-16%)** ✅
>   - `eval`: 0.87ms → **0.27ms (-69%)** ✅✅
>   - `no_eval`: 1.05ms → **0.96ms (-7%)** ✅
> - 全量回归 **665/665 pass**(原 660 + 5 new `CriteriaDebugTipMsgTest`)
> - `baseline.json` **更新**(4 scenario 全部改善)
>
> **JFR 验证(post-V5.94 → post-V5.95,30s HotPathBenchTest)**:
> - workload iters 43431 → 71820 (+65%) — 同样 35s 跑更多 fact
> - 4 addTipMsg 路径在 production hot path 中**完全消失**(debug=false)

## 1. 起因

post-V5.94 JFR 30s HotPathBenchTest 抓 top-1:
- `StringConcatHelper.prepend` 593 sample(其中 production ~85%,test fixture
  FactIds.next ~15%)

audit `StringConcatHelper` 在 production 的 callers,发现 `Criteria.evaluate`
4 个 addTipMsg 调用是无条件执行的 string concat:

```java
context.addTipMsg("计算条件：" + this.getId());  // line 34
context.addTipMsg("左值：" + leftId);             // line 38
context.addTipMsg("右值：" + valueId);            // line 115
context.addTipMsg("执行比较：" + this.op.toString());  // line 132
...
context.cleanTipMsg();  // line 135 — 立即把 builder 抹掉
```

4 个 addTipMsg 全部**无条件**执行,即使 `Rule.debug = false`。但末尾
`cleanTipMsg()` 立即把所有 append 的内容抹掉 — 整段是纯浪费。

V5.88 已修过 `CriteriaActivity.logMessage` 早返(同样 debug=false 时省
String.format + toString + MessageItem 分配),但 V5.88 没改到 `addTipMsg`
这 4 处。V5.95 补完这个缺口。

## 2. Audit:debug flag 流向

`Rule.debug` (V5.90 默认 false) 流向:
1. `Rule.debug` (model 层)
2. `CriteriaNode.isDebug()` (rete 结构层)
3. `CriteriaActivity` 构造器参数 `boolean debug` (runtime 层,V5.88 加的)
4. **`Criteria.debug` (V5.95 新加)** — `Criteria.evaluate` 内部用

`NodeActivityFactory.createCriteria` (V5.95 修改):是唯一 production 构造
`CriteriaActivity` 的地方(line 76),改成同时 `criteria.setDebug(node.isDebug())`
把 debug 传播到 Criteria。

`Criteria` 内部 `boolean debug` 字段:
- 默认 `false` (production-safe)
- 由 `NodeActivityFactory` 在构造 `CriteriaActivity` 时从 `node.isDebug()` 传入
- `Criteria.evaluate` 在 4 个 addTipMsg 调用前检查 `if (this.debug)`

## 3. 改动

### 3.1 production: `server/lib/ruleforge-core/src/main/java/com/ruleforge/model/rule/lhs/Criteria.java`

```diff
+ // V5.95 — debug 门控 addTipMsg 字段,默认 false (production-safe)。
+ @JsonIgnore
+ private boolean debug;

+ public boolean isDebug() { return this.debug; }
+ public void setDebug(boolean debug) { this.debug = debug; }

  public EvaluateResponse evaluate(EvaluationContext context, Object obj, List<Object> allMatchedObjects) {
-     context.addTipMsg("计算条件：" + this.getId());
+     if (this.debug) {
+         context.addTipMsg("计算条件：" + this.getId());
+     }
      ValueCompute valueCompute = context.getValueCompute();
      LeftPart leftPart = this.left.getLeftPart();
      String leftId = this.left.getId();
-     context.addTipMsg("左值：" + leftId);
+     if (this.debug) {
+         context.addTipMsg("左值：" + leftId);
+     }
      ...
-         context.addTipMsg("右值：" + valueId);
+         if (this.debug) {
+             context.addTipMsg("右值：" + valueId);
+         }
      ...
-     context.addTipMsg("执行比较：" + this.op.toString());
+     if (this.debug) {
+         context.addTipMsg("执行比较：" + this.op.toString());
+     }
      boolean result = context.getAssertorEvaluator().evaluate(leftResult, right, datatype, this.op);
      response.setResult(result);
+     // V5.95 — cleanTipMsg 始终调用(无 debug 门控),保留调用契约
+     // 保证下游 ActivationImpl.execute addTipMsg 行为正确(append 不会错加 ">>")
      context.cleanTipMsg();
      return response;
  }
```

### 3.2 production: `server/lib/ruleforge-core/src/main/java/com/ruleforge/runtime/rete/NodeActivityFactory.java`

```diff
+ import com.ruleforge.model.rule.lhs.Criteria;
  ...
  private static CriteriaActivity createCriteria(CriteriaNode node, Map<Object, Object> context) {
+     // V5.95 — 传播 debug 到 Criteria
+     Criteria criteria = node.getCriteria();
+     criteria.setDebug(node.isDebug());
-     CriteriaActivity activity = new CriteriaActivity(node.getCriteria(), node.isDebug());
+     CriteriaActivity activity = new CriteriaActivity(criteria, node.isDebug());
      ...
  }
```

### 3.3 BDD: `server/lib/ruleforge-core/src/test/java/com/ruleforge/model/rule/lhs/CriteriaDebugTipMsgTest.java` (新, 5 BDD tests)

- `DebugFalse.debugFalseSkipsAll4AddTipMsg`: 锁 `debug=false` → 0 `addTipMsg`
  调用(用 `verify(times(0))`)
- `DebugFalse.debugFalseCleanTipMsgStillCalled`: 锁 `debug=false` → 1 `cleanTipMsg`
  调用(保留调用契约)
- `DebugFalse.debugFalseThousandEvaluatesNoTipMsg`: 锁 1000 次 evaluate 后
  `addTipMsg` 仍 0 次(性能契约)
- `DebugTrue.debugTrueCallsAll4AddTipMsg`: 锁 `debug=true` → 4 `addTipMsg` + 1
  `cleanTipMsg` 全部调(V5.95 保留 V5.94 行为)
- `DefaultDebug.newCriteriaDefaultDebugFalse`: 锁 `new Criteria()` 默认
  `debug=false` (production-safe)

**TDD red → green 流程**:
- red: BDD 编译失败(无 setDebug/isDebug 方法)
- green: `Criteria.java` + `NodeActivityFactory.java` 改完,5/5 BDD pass

## 4. 验证

### 4.1 单元 + 全量回归

- `CriteriaDebugTipMsgTest`: **5/5 pass**
- `mvn test -pl lib/ruleforge-core`: **665/665 pass**(原 660 + 5 new),无回归
- 655 现有 tests 全部通过(debug 默认 false,production 行为保持)

### 4.2 JFR 30s HotPathBenchTest 抓取

V5.94 → V5.95 关键变化:
- workload iters 43431 → 71820 (+65%) — per-fact cost 砍 45%
- `addTipMsg` 在 production path 中**完全消失**(debug=false)
- `StringConcatHelper.prepend` 实际生产路径部分砍(production 路径不再 concat)
- 总 production string concat 减少 ≈ 4 addTipMsg × 2 criteria × N facts / 35s
  ≈ 千万级别 string concat 砍掉

### 4.3 Wall-time bench

**HotPathBenchTest 35s long-running**(3 次 re-run):

| run | per-fact | iters |
|---|---|---|
| V5.95 run 1 | 0.12us | 71918 |
| V5.95 run 2 | 0.12us | 70582 |
| V5.95 run 3 | 0.12us | 72452 |
| V5.95 median | **0.12us** | ~71800 |
| V5.94 median | 0.22us | 43431 |
| delta | **-45%** | **+65%** |

**`HotPathBenchTest` per-fact 砍 45% — 9 PR 周期最大单 PR 收益**。比 V5.92
(-9%)、V5.91 (-36%) 还大。

**EvalBenchmarkV579, 4 scenarios × 50 iter(3 次 re-run)**:

| scenario | V5.92 baseline p50 | V5.95 (3 runs) | median vs V5.92 |
|---|---|---|---|
| `no_eval_5r` (5 rule × 2-pattern) | 2.50ms | 1.92, 1.43, 1.43 | **1.43ms (-43%)** ✅ |
| `no_eval_3way` (3-pattern) | 0.83ms | 0.68, 0.68, 0.66 | **0.68ms (-16%)** ✅ |
| `eval` (no match) | 0.87ms | 0.29, 0.27, 0.27 | **0.27ms (-69%)** ✅✅ |
| `no_eval` (2-pattern) | 1.05ms | 0.95, 0.96, 0.98 | **0.96ms (-7%)** ✅ |

**`eval` scenario 砍 69%** — 因为 `eval` 是 no-match 路径,所有 evaluation
跑但 rule 不 fire,V5.95 砍掉的 4 addTipMsg 全部在 evaluation 阶段,无
rule-fire 路径可摊销。

**`baseline.json` 更新**:
```diff
- "p50": 2.50,  // no_eval_5r
+ "p50": 1.43,
- "p50": 0.83,  // no_eval_3way
+ "p50": 0.68,
- "p50": 0.87,  // eval
+ "p50": 0.27,
- "p50": 1.05,  // no_eval
+ "p50": 0.96,
```

## 5. 经验教训

1. **V5.88 + V5.95 配对完成"debug 门控"双闭环**:
   - V5.88:`CriteriaActivity.logMessage` 早返(`executeMessageItems` 路径)
   - V5.95:`Criteria.evaluate` 4 `addTipMsg` 门控(`tipMsgBuilder` 路径)
   - 两者都是 `Rule.debug = false` 时副产物的浪费,同一类问题两个修复点
2. **StringConcatHelper 是 hidden perf killer**:
   - `"X：" + value` 这种"看似无害"的 string concat,JIT 编译为
     `StringConcatHelper.makeConcatWithConstants` 走 StringBuilder,
     每次分配 + append + toString
   - 在 per-fact hot path × 千万次调用 = 不可忽视的 cost
   - debug 门控砍掉整个 string concat path,per-fact -45%
3. **addTipMsg 末尾的 cleanTipMsg 决定设计**:
   - 如果 addTipMsg 和 cleanTipMsg 都门控 → 下游 ActivationImpl.execute
     addTipMsg 会错加 ">>" 前缀(因为 builder 残留状态)
   - V5.95 选择:4 addTipMsg 门控,cleanTipMsg 始终调(noop 但保留契约)
4. **bench Rule.debug = false 的"奖励"**:
   - production 路径 `Rule.debug` 默认 false(V5.90)
   - bench 显式 `setDebug(false)` (V5.90 doc 要求)
   - V5.95 之前 V5.88 早返已让 `executeMessageItems` 路径免开销
   - V5.95 进一步让 `tipMsgBuilder` 路径免开销,两个 debug-only 副产物都
     完整门控

## 6. V5.95 真实定位

**V5.95 是 perf 突破,9 PR 周期最大单 PR 收益**:
- ✅ wall-time 砍 45% per-fact (V5.94 0.22us → V5.95 0.12us)
- ✅ EvalBenchmark 4 scenario 全部改善,`eval` 砍 69%
- ✅ JFR `addTipMsg` 在 production path 完全消失
- ✅ 跟 V5.88 `logMessage` 早返配对完成 debug 门控双闭环
- ⚠️ `baseline.json` 需更新(4 scenario p50 全部下降)

**对比 V5.88 (logMessage 早返)**:
- V5.88 砍 `executeMessageItems` 路径的 String.format + toString + MessageItem 分配
- V5.95 砍 `tipMsgBuilder` 路径的 string concat + StringBuilder.append
- 两者都是 debug-only 副产物,V5.88 + V5.95 = 完整 debug 门控闭环

**对比 V5.92 (flat sticky list)**:
- V5.92 resetStickyStateOnly -94% sample,per-fact -9% (4 个抽象复合)
- V5.95 addTipMsg 完全消失,per-fact -45% (debug 门控,单点但影响大)
- V5.95 收益 > V5.92,因为 string concat 在 per-fact hot path 中是主要 cost

## 7. 改动清单

- production: `server/lib/ruleforge-core/src/main/java/com/ruleforge/model/rule/lhs/Criteria.java`
  (加 `debug` field + 4 addTipMsg `if (this.debug)` 门控,cleanTipMsg 保留)
- production: `server/lib/ruleforge-core/src/main/java/com/ruleforge/runtime/rete/NodeActivityFactory.java`
  (createCriteria 传 `node.isDebug()` 到 `criteria.setDebug()`)
- BDD: `server/lib/ruleforge-core/src/test/java/com/ruleforge/model/rule/lhs/CriteriaDebugTipMsgTest.java`
  (新, 5 BDD tests)
- baseline: `server/lib/ruleforge-core/src/test/resources/perf/baseline.json`
  (4 scenario p50 全部更新)
- 文档: 本文件

## 8. 引用

- [[v594-partvalue-double-lookup]] V5.94 PR(V5.95 起点 — post-V5.94 JFR
  StringConcatHelper.prepend 593 仍有 production 路径 4 addTipMsg)
- [[v593-evaluationcontext-double-lookup]] V5.93 PR(同 V5.94 模式:HashMap 双 lookup)
- [[v592-flat-sticky-list]] V5.92 flat sticky list
- [[v591-factids-atomiclong]] V5.91 FactIds
- [[v590-rule-debug-default-flip]] V5.90 Rule.debug 默认翻转
- [[v589-getobjectproperty-reflection-cache]] V5.89 反射缓存
- [[v588-logmessage-early-return]] V5.88 logMessage 早返(V5.95 配对 — 同一
  debug 门控双闭环)
- [[v587-jfr-flamegraph]] V5.87 JFR 原始数据
- [[v586-findobject-classcache]] V5.86 ConcurrentHashMap 模式
- `target/v594.jfr` V5.94 baseline(StringConcatHelper.prepend 593)
- `target/v595.jfr` V5.95 验证(workload iters +65%)
