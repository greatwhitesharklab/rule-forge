# V5.90 — `Rule.debug` 默认值翻转(兑现 V5.87 JFR 76% 预测 + 修 V5.88 doc 误述)

> **TL;DR**:`Rule.debug` 构造默认从 `true` 翻到 `false` + `HotPathBenchTest` 显式
> `setDebug(false)`,让 V5.88 `CriteriaActivity.logMessage` 早返在 bench + 所有
> program-built Rule caller 上**真正生效**。
>
> **JFR 验证(V5.89 → V5.90,30s HotPathBenchTest)**:
> - `Formatter.format` 215 → **0** leaf sample (-100%) ✅
> - `Formatter.parse` 99 → 0
> - 全部 `Formatter.*` (V5.89 共 311 sample) → **0**
> - post-V5.90 top-1: `String.hashCode` 313(UUID 副作用)/ `resetStickyActivities` 275(V5.83 必需)
>
> **Wall-time 验证**(V5.88 → V5.90):
> - **HotPathBenchTest 35s per-fact 0.62us → 0.36us (-42%)**,iter 14121→24071 (+70%)
> - **EvalBenchmarkV579 no_eval 2.91ms → 1.22ms (-58%)** — V5.87 预测兑现
> - **EvalBenchmarkV579 no_eval_5r 5.50ms → 2.81ms (-49%)**
> - EvalBenchmarkV579 no_eval_3way 1.14ms → 0.92ms (-19%) / eval 1.23ms → 1.05ms (-15%)
> - PerfScaling single N=10000 0.68us → 0.63us (-7%) / dual 0.62us → 0.61us (持平)
> - 全量回归 **641/641 pass**

## 1. 起因

V5.88 (PR #151) 提交时 doc 写:"JFR `logMessage` 289→0 leaf sample" + "30-50% 收益预测"。
但 V5.88 + V5.89 (PR #152) **实测**:
- post-V5.89 JFR: `Formatter.format` 仍 **215 leaf sample** (跟 V5.88 doc 隐含的 0 矛盾)
- post-V5.88 wall-time: 0-12% per-fact 收益(跟 V5.87 doc 预测的 30-50% 矛盾)

**根因**:`CriteriaActivity.logMessage` 早返 `if (!this.debug) return;` **在 bench 上
从未触发**。原因链:
1. `HotPathBenchTest.buildDualClassRule` 用 `new Rule()` 直接构造
2. `Rule.java:35` 旧默认 `this.debug = true`
3. `BuildContextImpl.currentRuleIsDebug()` line 235 返 `true`
4. `new CriteriaNode(..., true)` → `new CriteriaActivity(..., true)`
5. `CriteriaActivity.debug=true` → 早返跳过 → `String.format` 每次 evaluate 跑

**JFR 100% 验证** post-V5.89 v589.jfr:
```
Formatter.format leaf: 412 total stacks, 402 cross user code
   402  com.ruleforge.runtime.rete.CriteriaActivity.logMessage(EvaluateResponse, Context) line: 88
```
402/402 user-reachable `Formatter.format` 全部栈回 `CriteriaActivity.logMessage:88` —
V5.88 doc 误述,76% JFR hot path 收口**只在 `debug=false` 路径生效**。

## 2. Audit: 翻转 `Rule.debug = true → false` 安全吗?

**已验证**:
- `BuildContextImpl.currentRuleIsDebug()` (line 235) null-safe:`getDebug() != null && getDebug()`
- XML parser `AbstractRuleParser.java:46-48` 已读 `<rule debug="true|false">` → `rule.setDebug(...)`
- 所有 `isDebug/getDebug` consumer **observability only,无 control flow**:
  - `CriteriaActivity` 构造 / `CriteriaNode.isDebug()` (criteria log)
  - `ScorecardResourceBuilder` / `ComplexScorecardRulesBuilder` / `CrosstabRulesBuilder` / `DecisionTableRulesBuilder` / `DecisionTreeRulesBuilder` (rule copy)
  - `LoopRule.java:54, 169` / `ActivationImpl.java:79` (action debug)
  - `ScoreRule.java:47` (score log)
- **app 模块**(`console-app` / `executor-app` / `console`): 零 `setDebug` 调用
- **测试**: 5 个 `setDebug` 调用全部显式(`scorecard.setDebug(false)` 等),不依赖 `Rule` 构造默认

**结论**:**safe 翻转**。无 production code path 依赖默认 `true` 做事。

## 3. 改动

### 3.1 production: `server/lib/ruleforge-core/.../Rule.java`

```java
public Rule() {
    // V5.90 — 默认 debug=false 让 V5.88 早返 (CriteriaActivity.logMessage) 默认生效。
    this.debug = false;
}
```

### 3.2 bench: `server/lib/ruleforge-core/.../HotPathBenchTest.java`

```java
private Rule buildDualClassRule(String personName, String street) {
    Rule r = new Rule();
    r.setName("R1"); r.setSalience(0);
    // V5.90 — 显式 debug=false 让 V5.88 早返生效(跟 Rule.java:35 默认翻转一致)
    r.setDebug(false);
    // ...
}
```

显式 setDebug(false) 是**防御性**:即使未来 Rule 默认被改回 true,bench 仍走 fast path。

### 3.3 BDD: `server/lib/ruleforge-core/.../RuleDebugDefaultTest.java` (新, 3 tests)

- `newRuleDefaultsDebugFalse`: 锁 V5.90 翻转后 `new Rule().getDebug() == false`
- `explicitSetDebugTrue`: 锁显式 setDebug(true) 透传
- `explicitSetDebugFalse`: 锁显式 setDebug(false) 跟默认一致(幂等)

**TDD red → green 流程**:
- red: `newRuleDefaultsDebugFalse` fail (旧默认 `true` 跟契约 `false` 矛盾)
- green: Rule.java:35 翻 + HotPathBenchTest 加 setDebug(false) 后 3/3 pass

## 4. 验证

### 4.1 单元 + 全量回归

- `RuleDebugDefaultTest`: **3/3 pass**
- `mvn test -pl lib/ruleforge-core`: **641/641 pass** (原 638 + 3 新),无回归

### 4.2 JFR 二次抓取(同 workload,30s HotPathBenchTest)

V5.90 top-15 leaf sample(1960 total,V5.89 是 1962):

| hot method | V5.89 sample | V5.90 sample | 变化 |
|---|---|---|---|
| **`Formatter.format` (all overloads)** | **311** (215+96) | **0** | **-100%** ✅ |
| `Formatter.parse` | 99 | 0 | -100% |
| `String.hashCode` (UUID 副作用) | 144 | 313 | UUID 调用频率上升(workload 提速) |
| `resetStickyActivities` | 137 | 275 | workload 提速 +70% |
| `StringBuilder.appendChars` | 156 | 0 (≤25 阈值) | -100% (无 String.format toString 触发了) |
| `SecureRandom` UUID 链 | ~250 | 430 | UUID 调用 +70% |
| `DirectMethodHandleAccessor.invoke` (V5.89 反射缓存) | 38 | 75 | workload 提速 |
| `StringConcatHelper.newString` | (≤25) | 74 | 其他 String concat 浮上来 |

**核心 fix 100% 兑现**:`Formatter.*` V5.89 311 sample → V5.90 0 sample。

### 4.3 Wall-time bench

**HotPathBenchTest 35s long-running**(同 V5.87 JFR workload):

| V5.79 | V5.83 | V5.88 | V5.89 | V5.90 |
|---|---|---|---|---|
| (没跑) | (没跑) | 0.68us / 51.16M | 0.62us / 56.5M | **0.36us / 96.3M** |

- V5.79 → V5.90:per-fact 0.36us,V5.79 的 < 1ms 是 bug-driven 便宜数据(详见 V5.85 note)
- V5.89 → V5.90:**per-fact -42%**,iter +70%,facts +70%
- 真实渐近线:0.36us per-fact(之前 V5.85 测的 0.68us 是 logMessage 浪费态)

**EvalBenchmarkV579, 4 scenarios × 50 iter**:

| scenario | V5.83 | V5.88 | V5.89 | V5.90 | V5.90 vs V5.88 |
|---|---|---|---|---|---|
| `no_eval` (2-pattern) | 3.0ms | 2.91ms | 2.70ms | **1.22ms** | **-58%** |
| `no_eval_3way` (3-pattern) | 1.3ms | 1.14ms | 1.03ms | **0.92ms** | **-19%** |
| `no_eval_5r` (5 rule × 2-pattern) | 5.2ms | 5.50ms | 5.35ms | **2.81ms** | **-49%** |
| `eval` (no match) | 1.2ms | 1.23ms | 1.12ms | **1.05ms** | **-15%** |

**PerfScalingAnalysisTest, N=10000**:

| class | V5.85 | V5.88 | V5.89 | V5.90 |
|---|---|---|---|---|
| single | 0.68us | 0.68us | 0.62us | 0.63us |
| dual | 1.10us | 1.10us | 0.62us | 0.61us |

PerfScaling 持平(short bench,JIT 启动限制);真渐近线在 HotPathBenchTest 35s 看。

## 5. V5.88 doc 误述修正

V5.88 提交时 doc 写:
> "JFR `logMessage` 289→0 leaf sample"
> "30-50% per-fact 收益"

实际 V5.88 后:
- JFR `Formatter.format` 仍 215+96 = 311 sample(V5.89 测得),V5.88 doc **没说前提**是
  `Rule.debug=false`
- Wall-time 0-12%(V5.88 测得),**未兑现** 30-50% 预测

**根因**:V5.88 跟 V5.90 是**两个独立 fix**:
- V5.88 修 `CriteriaActivity.logMessage` 早返逻辑(code 100% 正确)
- V5.90 修 `Rule.debug` 默认值 + bench 显式 setDebug(false)(让 V5.88 早返**真正生效**)

V5.88 单独 PR 不带 V5.90,JFR hot path 收口**只对 production `debug=false` 路径生效**;
对 bench fixture + program-built Rule caller **无效**。V5.88 doc 应该强调"前提"
(`Rule.debug=false`)。V5.90 修正这层。

## 6. 改动清单

- production: `server/lib/ruleforge-core/src/main/java/com/ruleforge/model/rule/Rule.java`
  (1 行 ctor 翻转)
- bench: `server/lib/ruleforge-core/src/test/java/com/ruleforge/rete/perf/HotPathBenchTest.java`
  (1 行 `r.setDebug(false);`)
- test: `server/lib/ruleforge-core/src/test/java/com/ruleforge/model/rule/RuleDebugDefaultTest.java`
  (新, 3 BDD tests)
- baseline: `server/lib/ruleforge-core/src/test/resources/perf/baseline.json` (4 scenario p50 大幅下调)
- 文档: 本文件
- JFR: `target/v590.jfr` (3.2MB, 30s)

## 7. 后续候选(post-V5.90 JFR)

V5.90 后 top hot method:
1. `String.hashCode` 313 sample(16% hot path)— UUID.randomUUID 副作用(测试用 UUID
   标记 fact,production 可能不用 UUID)
2. `resetStickyActivities` 275 sample(14%) — V5.83 必需,优化空间有限
3. `SecureRandom` UUID 链 230+ sample — 跟 #1 同源
4. `Method.invoke` (`DirectMethodHandleAccessor.invoke`) 75 sample — JIT 已优化

V5.91+ 候选方向:
- `Utils.UUID` 替换为 `AtomicLong`/`ThreadLocalRandom`(若 production 无 UUID 需求)
- `Criteria.evaluate` 内部 `String.format` 路径排查(无, JFR 验证 post-V5.89 不存在)

## 8. 引用

- [[v587-jfr-flamegraph]] V5.87 JFR 原始 76% 预测
- [[v588-logmessage-early-return]] V5.88 PR(code 正确,doc 误述)
- [[v589-getobjectproperty-reflection-cache]] V5.89 反射缓存
- [[v586-findobject-classcache]] V5.86 ConcurrentHashMap 模式
- `target/v589.jfr` (V5.89 验证 Formatter.format 仍 311 sample)
- `target/v590.jfr` (V5.90 验证 Formatter.format → 0)

## 9. 经验教训

1. **JFR 占比 = wall-time 占比 — 但前提要满足** — V5.87 预测 30-50%,V5.88 兑现 0-12%,
   **不是因为 V5.88 错了**,而是 V5.88 fix 100% 正确但**未触发**(`Rule.debug=true`
   早返跳过)。完整 perf 收益需要 V5.88 + V5.90 联动
2. **构造默认值是隐形 perf 雷** — `Rule.debug=true` 旧默认让 V5.88 早返在
   `new Rule()` 路径全部失效,production 用户全部付 cost。Rule/Lhs 构造默认值要
   慎重,observability 字段不应默认开
3. **Audit 才知 safe** — 翻 `Rule.debug` 默认看似危险(可能影响 production debug
   输出),但 audit 显示所有 consumer 都是 observability,无 control flow,翻默认
   是 safe 的
4. **bench 显式 setDebug 是防御性** — 即使 Rule 默认改了,bench 仍走 fast path
5. **JFR 100% 准确** — V5.89 抓 402/402 Formatter.format 来自 CriteriaActivity.logMessage
   100% 验证 V5.88 fix 未触发;V5.90 抓 0 验证 V5.88 fix 兑现
6. **V5.88 doc 误述修正 = 重要 PR 价值** — 不只是 perf 收益,还修了一个
   "JFR 数据表面合规但实际未生效"的 doc 陷阱
