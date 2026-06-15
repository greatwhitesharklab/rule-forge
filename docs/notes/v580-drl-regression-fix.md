# V5.80 — DRL 回归修复 + 端到端 BDD 锁约

## 背景

V5.78 PR #142(DRL grammar 扩)漏了 `DrlDeserializer.toCriteria` 构造
`VariableLeftPart` 时调 `setVariableCategory`,导致 V5.78+ ReteBuilder 路径
`BuildContextImpl.getObjectType` (line 92) 抛
`"Variable category [null] not exist"`。

V5.79 perf bench(`EvalBenchmarkV579`)在 V5.79 装上后用**手工构造 Rule** 绕开
DRL parser,workload 保留 1000 Person + 1000 Address × 3 rule 形态,perf
数字仍 valid,但 firedRules=0 是 known gap(注释误以为是 V5.78 DRL 漏填导致,
V5.80 实测发现手构 Rule 路径也有独立 fired=0 bug,见 TD-18.4)。

## V5.80 修法(TD-18.0)

在 `PropertyCriteria` 加 `factType` 字段,DRL parser 构造时从
`DrlPatternContext.UPPER_IDENTIFIER().getText()` 灌父 fact type;
`DrlDeserializer.toCriteria` 转 `VariableLeftPart` 时
`part.setVariableCategory(pc.getFactType())`(原本 V5.78 漏)。
跟 `RulesRebuilder` NamedJunction 老路径对齐(`RulesRebuilder.java` line 152-154)。

文件:
- `server/lib/ruleforge-core/src/main/java/com/ruleforge/model/rule/lhs/PropertyCriteria.java` — 加 `factType` 字段 + getter/setter + javadoc
- `server/lib/ruleforge-core/src/main/java/com/ruleforge/ir/drl/DrlDeserializer.java` — `buildPropertyCriteria` 灌 `pc.setFactType(...)`;`toCriteria` 调 `part.setVariableCategory(...)`

## 端到端 BDD(TD-18.1)

新文件 `server/lib/ruleforge-core/src/test/java/com/ruleforge/ir/drl/DrlReteIntegrationTest.java`,
3 个 BDD 测试(BDD:Given/When/Then 写在 `@DisplayName`):

1. `singlePatternFieldFilter` — DRL `Applicant(age > 18)` → ReteBuilder → 不抛错
2. `twoPatternJoinBuilds` — DRL `Applicant(name == "alice"), Loan(applicantName == "alice")` → ReteBuilder → fireRules 不抛错
3. `multipleFactTypesDistinctCategories` — Applicant + Loan 双 fact type,各自 variableCategory 正确传递

**反向验证**: 把 V5.80 fix stash 掉,跑本 BDD — 3 个全部抛
`RuleException: Variable category [null] not exist`,精确在 V5.78 漏点;
恢复 fix 后 3 个全过。**契约已锁** — 未来任何再破坏这条路径的改动会被本 BDD 立刻逮到。

装配:跟 `EvalBenchmarkV579` 一致无 Spring 套路
(`EngineContext.init(mockRegistry)` + AndBuilder+CriteriaBuilder 反射注入 +
AssertorEvaluator 反射灌 EqualsAssertor + ValueCompute Mockito mock + 手构
`ResourceLibrary` 含 `VariableCategory` + 手构 JavaBean POJO Applicant/Loan
—— Commons BeanUtils 要求 getter/setter 形式,public 字段不够
[NoSuchMethodException,见 PR 调试 trace])。

为啥不复用 V5.42.5 `DrlEndToEndTest` — 后者只验 `DrlDeserializer` 出口
Rule 列表结构,不走到 `ReteBuilder.buildRete` → `BuildContextImpl.getObjectType`。
V5.78 漏的 `variableCategory` 字段是在 ReteBuilder 端才被用到,
`DrlEndToEndTest` 走不到那条路径,不能逮 V5.78 回归。

## V5.79 perf bench 收紧(TD-18.2) — 部分完成,留 TD-18.4

V5.79 bench 4 个 scenario 当前 firedRules 全部 0,断言注释原本写"V5.80 修
V5.78 DRL 回归后本断言会变严格"(即收紧到 fired=3)。V5.80 实测:

- V5.80 修的是 **DRL parser 路径** `DrlDeserializer.toCriteria` 的 `setVariableCategory` 漏填
- V5.79 bench 走的是 **手工构造 Rule** 路径,本身 `variableCategory` 一直有设
- 两条路径的 `setVariableCategory` 都是 "Person" / "Address",但 V5.79 bench 仍 fired=0

**结论**: V5.79 bench fired=0 是 V5.78+ Criteria → CriteriaActivity 路径的
**第二个独立 bug**,跟 V5.78 DRL 回归是 2 个不同问题。V5.80 收口仅修
V5.78 DRL 漏填;V5.79 bench 收紧到 fired=3 留给 TD-18.4 调查,见
`task #42`。

V5.80 暂保留 V5.79 bench 的 `fired=0` 断言 + 注释更新,指向 TD-18.4。
baseline.json 同步更新 note 说明两个 bug 的区分。

## Test count

- 修前(只 TD-18.0): 612 非 perf + 5 perf = 617 total
- 修后(V5.80): 615 非 perf(`+3` from DrlReteIntegrationTest) + 5 perf = **620 total**,0 failure,0 error

## Files changed

| File | +/- | 用途 |
|---|---|---|
| `model/rule/lhs/PropertyCriteria.java` | +9 | 加 factType 字段 |
| `ir/drl/DrlDeserializer.java` | +10 | toCriteria setVariableCategory + buildPropertyCriteria setFactType |
| `test/.../ir/drl/DrlReteIntegrationTest.java` | +214 | 新 BDD 端到端契约锁 |
| `test/.../rete/perf/EvalBenchmarkV579.java` | +12/-9 | 收紧注释 → 指向 TD-18.4 |
| `test/.../resources/perf/baseline.json` | +1/-1 | no_eval note 区分 2 个 bug |
| `docs/notes/v580-drl-regression-fix.md` | +新增 | 本文件 |

## 后续

- **TD-18.4**(task #42): V5.79 hand-built bench fired=0 调查
  - 候选原因:`Criteria.evaluate` → `ValueCompute.findObject` 路径在 V5.78+ 改动后行为变化
  - 或 `ObjectTypeActivity.enter` 路径在 V5.78+ 改动后过滤太严
  - 调查方法: 给 hand-built bench 加细粒度 trace,看 ReteBuilder 建的节点图
- **V5.81+**: TD-18.4 修完后,re-tighten `EvalBenchmarkV579` no_eval / 3way / 5r 到 fired=3 / 1 / 3
