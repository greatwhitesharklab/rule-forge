# V5.81 — EngineContext 测试装配 bug 修 + 2 个独立 bug 区分

## 背景

V5.79 perf bench `EvalBenchmarkV579` 4 scenario firedRules 全 0,V5.80 修法收口时归
因"V5.78 DRL 漏填",在 [[v580-drl-regression-fix]] TD-18.2 bench 收紧注释留 TD-18.4
后续调查。V5.81 phase 19 实际调查发现:

- **2 个独立 bug**:
  1. **测试装配 bug(本 V5.81 修)**: `EvalBenchmarkV579` / `DrlReteIntegrationTest`
     用 Mockito mock `ValueCompute`,只 stub `findObject`。Mockito mock 默认
     `complexValueCompute` 返 null → `criteria.evaluate` 的 right side 永远是
     null → `equals(null)` 永不命中 → 所有 rule 都不 fire。
  2. **production bug(留 V5.82+, task #48)**: `KnowledgeSessionImpl.allFactsMap`
     是 `Map<String,Object>`,按 className 作 key,1000 个 Person insert 后只保留
     最后一个(同 key 覆盖)。`session.insert(fact)` 只 add 不 reevaluate,`fireRules`
     走 `allFactsMap.values()` 只看到 1 Person + 1 Address,3 条 special-pair rule
     都不匹配。生产路径是 `assertFact` 走 `reevaluate` 单独处理,本 bench 走的是
     错误路径。

## V5.81 修法(TD-19.0+1+2)

**新文件 `server/lib/ruleforge-core/src/test/java/com/ruleforge/rete/test/EngineContextWirer.java`**:
共享无 Spring 装配套路 helper,关键修复 — 用**真实 ValueCompute 实例**(无状态 +
public no-arg ctor 的纯函数类,见 `ValueCompute.java` line 38-44),不 Mockito
mock。`findObject` 走真实 impl 即可(本身行为就是"className 匹配返 fact 自身")。

`EvalBenchmarkV579` / `SingleRuleFiresBDD` / `DrlReteIntegrationTest` 都切到
`EngineContextWirer.wire()`,消除重复 mock registry 套路。

## 端到端 BDD 验证(TD-19.0)

新文件 `server/lib/ruleforge-core/src/test/java/com/ruleforge/rete/perf/SingleRuleFiresBDD.java`:
1 rule / 1 pattern / 1 fact 最小场景,期望 firedRules=1。

**反向验证**: 装 V5.79 老 mock 套路(Mockito ValueCompute 没 stub
complexValueCompute)→ firedRules=0,精确在 `criteria.evaluate` 的 right side
永远是 null(trace 看到 `rightResult=null` 配 `leftResult=alice`)。
**V5.81 装回真实 ValueCompute → firedRules=1**。契约已锁。

## EvalBenchmarkV579 收紧(暂不收紧,留 V5.82+)

V5.81 修测试装配后,SingleRuleFiresBDD 证明引擎工作。但 `EvalBenchmarkV579`
4 scenario **仍 firedRules=0** — 不是 V5.78+ 代码 regression,是 pre-existing
production bug `allFactsMap` 按 className 覆盖。

Trace 数据:
- 2000 个 fact insert + 一次 fireRules 期望 ~6000 criteria.evaluate 调用
- 实测 165 调用/iteration(每 iteration 5 warmup + 50 measure = 55 × 3 = 165)
- 165 / 3 rules = 55 fact evaluations per iteration,即只有 55 个 fact 到达
  criteria 路径(应该是 1000 person × 3 rules = 3000)
- trace 显示 left=UUID(一个 Person 的随机名)被 3 个 rule 反复 evaluate

**根因**: `allFactsMap` 是 `Map<String,Object>`,key=className。1000 Person insert
后 `allFactsMap.put("com.ruleforge...Person", fact)` 1000 次,只剩最后一个
Person(others 覆盖)。同样的 1000 Address 也只剩最后一个。所以 `allFactsMap.values()`
返 2 个 fact(1 Person + 1 Address),刚好是最后 insert 的 Person(随机 UUID 名)
+ 最后 insert 的 Address(随机 UUID street),跟 3 条 special-pair rule 都不匹配。

**修法(V5.82+, task #48)**: 把 `allFactsMap` 改成 `Map<String,List<Object>>` 或
干脆用 `List<Object>` 替代。或:让 `session.insert` 走 `assertFact` 路径(addToFactsMap
+ reevaluate),这样 insert 时就 push 到 rete 而不是 fireRules 才统一走。

**V5.81 收口**: `EvalBenchmarkV579` 4 scenario 仍 assertEquals(0, firedRules) +
注释更新指向本文件 + task #48。

## Test count

- 修前(V5.80 main): 615 非 perf + 5 perf = **620 total**
- 修后(V5.81): 615 非 perf(`SingleRuleFiresBDD` 是新增的 1 个,跟 V5.80 main
  持平 — V5.80 已包含 `SingleRuleFiresBDD` 之前写的"先 fail"版) + 5 perf = **620 total**,0 failure

## Files changed

| File | +/- | 用途 |
|---|---|---|
| `rete/test/EngineContextWirer.java` | +99(新) | 共享无 Spring 装配 helper,真实 ValueCompute |
| `rete/perf/SingleRuleFiresBDD.java` | +124(新) | 最小 1-rule fired=1 契约 BDD |
| `rete/perf/EvalBenchmarkV579.java` | +12/-55 | 切到 EngineContextWirer + 注释更新 |
| `ir/drl/DrlReteIntegrationTest.java` | +2/-43 | 切到 EngineContextWirer(DRL 端到端契约仍锁) |
| `docs/notes/v581-criteria-path-fix.md` | +新增 | 本文件 |

## 后续 V5.82+(task #48)

`KnowledgeSessionImpl.allFactsMap` 改成 `Map<String,List<Object>>` 或 `List<Object>`。
修完后:
- `EvalBenchmarkV579` no_eval 收紧到 firedRules=3
- no_eval_3way 收紧到 firedRules=1
- no_eval_5r 收紧到 firedRules=3
- eval 保持 firedRules=0(无 binding 或 value 不可达)
- baseline.json 同步

## 关键教训

1. **不要 Mockito mock 无状态 utility 类** — 容易漏 stub 导致静默 bug。`ValueCompute`
   是无状态 + public ctor,直接 `new ValueCompute()` 比 mock 简单且无错。
2. **trace 数据要分阶段打** — CriteriaActivity.enter + ValueCompute.complexValueCompute
   + AssertorEvaluator.evaluate 三层 trace 才能定位"right side 永远是 null"。
3. **pre-existing bug 跟 V5.78+ regression 区分清楚** — V5.80 注释说 V5.78 DRL
   修后 bench 会变严格,实际是另一个 pre-existing bug。诚实区分,不要为了 PR
   narrative 把两个 bug 强行合并。
