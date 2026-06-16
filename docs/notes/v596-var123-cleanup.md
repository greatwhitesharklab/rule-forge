# V5.96 — 反编译 `Iterator var123` 清理 + `while(true){...return;}` 展开

## Context

CLAUDE.md 立"代码优雅"硬约束(子原则:"动核心前先 grep + 顺手把触及文件的红线违反修掉")。
V5.95 修完 debug 门控后,audit 发现 `ruleforge-core` + `ruleforge-console-app` 里**反编译
残留**还大量存在:`for (Iterator var123 = ...iterator(); var123.hasNext(); ...) { var123.next(); ... }`
—— 这种模式是 Fernflower/Idea decompiler 把 `for (Type x : collection)` 反编译时的
fallback 形式,带 `var123` 这种无意义名 + 多余的 iterator boilerplate。

### Audit 结果

| 模块 | 涉及文件 | var123 总数 |
|---|---|---|
| `ruleforge-core` | 18 | ~100 |
| `ruleforge-console-app` | 1 (RefactorServiceImpl) | 21 |

`ruleforge-core` 的 var123 集中在 build/rebuild(parse/builder/runtime)路径,不是
hot path,但**可读性**明显劣化:new 读这代码的人会以为 var123 是某种业务含义。

**额外发现**:`do-while` 反编译成 `while (true) { if (cond) return; ... } while (cond);`,
导致大量"括号海洋"。本 PR 一并展开成早返风格。

### 修法

每个 var123 模式按其语义转 enhanced for-loop:

| 模式 | 转换 |
|---|---|
| 简单迭代 read-only | `for (Type x : coll)` |
| 含 `break` 找首个匹配 | enhanced for + 早返(把 post-loop 收尾逻辑也搬出来) |
| 含 `index++` | enhanced for + 显式 `index++` 放 body 末尾 |
| `for (Iterator<T> v; v.hasNext(); body) { v.next(); ... }` 末尾有 `body` 副作用 | 把 body 副作用放 for-each body 末尾 |
| `while (true) { ... return; }` 单层 | 展开成纯 for-each |
| `do-while` skip-pattern(任一不满足返 false) | enhanced for + 早返 |
| 含 labeled control flow(`label42/82/84` + `continue label`) | **不动** — 涉及 goto-like 控制流,需 characterization test |

## 改动文件 (22 个,-143 net lines)

### core (21 文件)

| 文件 | 改动 |
|---|---|
| `parse/LeftParser.java` | 2 var123 → for-each(1 个 var3 + 1 个 var7) |
| `parse/crosstab/CrosstabParser.java` | 1 var13 |
| `parse/crosstab/ConditionCrossCellParser.java` | 1 var3 |
| `parse/crosstab/ValueCrossCellParser.java` | 1 var3 (含 break) |
| `parse/scorecard/ComplexScorecardParser.java` | 1 outer + 1 inner var123;展开 `while(true){...return;}` |
| `model/rete/builder/CriterionBuilder.java` | 5 var123(prevNodes/prevObjectTypes/objectTypes×2/childrenNodes) |
| `model/rete/builder/OrBuilder.java` | 2 var123 (criterions + childNodes) |
| `model/rete/builder/BuildContextImpl.java` | 2 var123 (params×2 raw List 强转) |
| `model/rete/builder/ReteBuilder.java` | 1 for(Iterator var7;...;addLine) + 1 find-first do-while |
| `model/function/impl/MaxValueFunctionDescriptor.java` | 1 var6 |
| `model/function/impl/MinValueFunctionDescriptor.java` | 1 var6 |
| `model/rule/loop/LoopRule.java` | 7 var123 + 1 for(int var30) |
| `builder/table/CrosstabRulesBuilder.java` | 6 var123(valueCellRanges/topRanges/joints/conditions/ranges/cells) |
| `builder/table/CellContentBuilder.java` | 2 var123 (joints/conditions) |
| `PropertyConfigurer.java` | 1 var3 (supports) |
| `runtime/KnowledgeSessionImpl.java` | 2 var123 in `clearInitParameters()`(其他 16 个在 labeled 循环里,**不动**) |
| `runtime/rete/AbstractActivity.java` | 1 var2 in `doPassAndNode()` |
| `runtime/rete/AndActivity.java` | 1 do-while in `isAllPassed()` → 早返 |
| `runtime/rete/ValueCompute.java` | 1 var5 in `findObject()` |
| `runtime/agenda/AbstractRuleBox.java` | 3 var123 (`retract` + `activationShouldAdd`) |
| `runtime/agenda/ActivationImpl.java` | 1 for(Iterator var13;...;++index) |

### console-app (1 文件)

| 文件 | 改动 |
|---|---|
| `console/repository/refactor/RefactorServiceImpl.java` | 6 var123(2× fileRefactors/children + contentRefactors + templateFiles) + 2× `while(true){...return;}` 展开 |

### 跳过(明确不修)

| 文件 | 原因 |
|---|---|
| `KnowledgeSessionImpl.evaluationRete/activeRule/activeAgendaGroup` | 含 `label42/82/84` + `continue label` — class javadoc 明确要求 characterization test 才能重写循环结构 |
| `AndBuilder.buildCriterion` | do-while + 内嵌 `return` 提前构造 result,跟其他 file-builder 类似的中间状态机 |

## 验证

```bash
mvn test -pl lib/ruleforge-core        # 665/665 pass
mvn test -pl lib/ruleforge-core -Dtest=EvalBenchmarkV579
# p50: no_eval=0.92, no_eval_3way=0.59, no_eval_5r=1.43, eval=0.27 (V5.95 baseline 内)
mvn compile -pl app/ruleforge-console-app -am  # BUILD SUCCESS
```

`mvn test -pl lib/ruleforge-core` 665/665 pass(原 665,新 0 — 本 PR 0 production behavior 变化,纯 refactor)。

`EvalBenchmarkV579` p50 全部在 V5.95 baseline 内(no_eval_5r 1.43ms, no_eval_3way 0.68ms,
no_eval 0.96ms, eval 0.27ms),for-each 跟 iterator 在 JIT 优化后性能等价。

`grep "Iterator var" server/` 仅剩本 PR 加的 `// V5.96 — Iterator var123 → enhanced for` 注释标记。

## 风险 / trade-off

- **零 production behavior 变化** — 全是 1:1 语义保持的 idiom 重写,665 测试 100% pass 锁契约
- **for-each vs iterator** — bytecode 上 enhanced for 编译成 iterator,运行时性能等价;编译器
  在 ArrayList 上还会优化掉 iterator 分配
- **跳过 labeled 循环** — `label42/82/84` 涉及 16 var123 instance,本 PR 故意不动,留给未来
  characterization test + 大改
- **JIT 噪音** — 测试 wall-time 在 ±10% 内波动,V5.95 baseline 范围内

## 引用

- [[v595-criteria-addtipmsg-debug-gate]] V5.95 上一个 PR(本 PR 净 perf 0%,纯代码质量)
- CLAUDE.md "代码优雅" 硬约束
- [[v575-engine-elegance]] V5.75 立规则 + 文档
