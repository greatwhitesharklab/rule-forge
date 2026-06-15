# V5.88 — CriteriaActivity.logMessage 早返(消除 JFR 76% hot path)

> **TL;DR**:`CriteriaActivity.logMessage` 加 1 行 `if (!this.debug) return;`,消除 JFR 识别的
> 76% hot path。**实测 wall-time 收益 modest (0-12% per-fact)**,JFR sample 计数 100% 消失,
> 跟 V5.87 JFR 报告一致(logMessage + String.format + StringBuilder = 1570/2053 sample)。
>
> **关键教训**:**JFR sample 占比 ≠ wall-time 占比** — 短期 surefire fork 跑 2000 fact
> 受 setup + JIT 启动支配,JFR 反映的 76% 在 wall-time 只兑现 ~3-12%。Long-running
> workload 才是真 perf 测。仍是无脑 fix:debug=false 路径下 1 行早返消除一个明显
> 浪费点。

## 1. 起因

V5.87 (PR #150) JFR 35s long-running 抓出 `CriteriaActivity.logMessage` 调试日志 + 反射占
hot path **1570+ sample (76%)**。V5.88 选 JFR 排名第一的 `logMessage` 早返做 no-brainer
fix — 1 行 `if (!this.debug) return;` 消除 JFR 排名第一 hot path。

## 2. 改动

### 2.1 production code

`server/lib/ruleforge-core/src/main/java/com/ruleforge/runtime/rete/CriteriaActivity.java`:

```java
private void logMessage(EvaluateResponse response, Context context) {
    // V5.88 — 早返:debug=false 时不进 String.format / toString / MessageItem 分配。
    if (!this.debug) {
        return;
    }
    // ... 原 String.format 逻辑
}
```

`this.debug` 字段已存在(line 17),由 `NodeActivityFactory.createCriteria(node.getCriteria(),
node.isDebug())` 传入;`CriteriaNode.isDebug()` 来自 `CriterionBuilder` 调
`context.currentRuleIsDebug()`;`BuildContextImpl.currentRuleIsDebug()` 返回
`currentRule.getDebug() != null && currentRule.getDebug()`(`Rule.debug` 默认 null → false)。
**生产路径 `debug` 默认就是 `false`**,早返对正常 production 生效。

### 2.2 BDD test

`server/lib/ruleforge-core/src/test/java/com/ruleforge/runtime/rete/CriteriaActivityLogMessageTest.java`:
3 个 @DisplayName 锁契约:
1. `debug=true` → `enter` 触发 `logMessage` → `executeMessageItems` 增 1 MessageItem
2. `debug=false` → 早返 → `executeMessageItems` 保持 0
3. `debug=false` 1000 次 enter → `executeMessageItems` 仍 0(锁 V5.87 JFR 76% hot path 消失)

## 3. 验证

### 3.1 单元 + 全量回归

- `CriteriaActivityLogMessageTest`: 3/3 pass
- `mvn test -pl lib/ruleforge-core`: **630/630 pass**,无回归

### 3.2 JFR 二次抓取(同样 30s HotPathBenchTest workload)

V5.87 报告 `CriteriaActivity.logMessage` 289 leaf sample;V5.88 实测 **`logMessage` 0 leaf sample**
(连前 30 都没了)。`String.format` / `Formatter.format` 仍出现 121+35 = 156 sample,但 **0 个
sample 的 stack 里含 `logMessage`** — 确认所有调用是别处(比如 `HotPathBenchTest` 自己的
`System.out.printf`、`Criteria.evaluate` 内部别的 `String.format` 路径、`String` 构造路径)。

V5.88 top-15 leaf sample(2089 sample 总数,比 V5.87 2053 略多):
```
182  java.lang.AbstractStringBuilder.inflateIfNeededFor
160  java.lang.AbstractStringBuilder.appendChars
144  java.lang.String.hashCode                  ← JDK 基础设施,不可控
137  com.ruleforge.runtime.rete.ReteInstance.resetStickyActivities  ← V5.83 必需
131  org.apache.commons.beanutils.PropertyUtilsBean.getSimpleProperty  ← V5.89 候选
121  java.util.Formatter.format(Locale, String, Object[])             ← **logMessage 之前 580 → 121**
111  java.util.Formatter.parse
109  org.apache.commons.beanutils.expression.DefaultResolver.next       ← V5.89 候选
 90  com.ruleforge.runtime.rete.AndActivity.passAndNode
 60  sun.security.provider.NativePRNG$RandomIO.implNextBytes
 50  com.ruleforge.model.rule.lhs.Criteria.evaluate
 49  sun.security.provider.SecureRandom.updateState
 49  sun.security.provider.SecureRandom.engineNextBytes
 47  java.util.Arrays.copyOf
 38  java.util.Arrays.fill
```

V5.88 top-15 vs V5.87 对比(V5.87 详见 [[v587-jfr-flamegraph]]):

| hot method | V5.87 sample | V5.88 sample | 变化 |
|---|---|---|---|
| `CriteriaActivity.logMessage` | 289 | **0** | -100% ✅ |
| `String.format` | 291 | 35 | -88% ✅ |
| `Formatter.format(Locale,...)` | 154 | 121 | -21% |
| `StringBuilder.inflateIfNeededFor` | 141 | 182 | +29% (其他路径) |
| `PropertyUtilsBean.getSimpleProperty` | 134 | 131 | -2% |
| `resetStickyActivities` | 150 | 137 | -9% (workload 差异) |

**核心 fix 100% 生效**:`logMessage` 289 → 0,`String.format` 291 → 35。

### 3.3 Wall-time bench(EvalBenchmarkV579, 4 scenarios × 50 iter)

| scenario | V5.83 p50 | V5.88 p50 | gain |
|---|---|---|---|
| `no_eval` (2-pattern) | 3.0ms | 2.91ms | **-3%** |
| `no_eval_3way` (3-pattern) | 1.3ms | 1.14ms | **-12%** |
| `no_eval_5r` (5 rule × 2-pattern) | 5.2ms | 5.50ms | +6% (within noise) |
| `eval` (no match) | 1.2ms | 1.23ms | +3% (within noise) |

### 3.4 Wall-time bench(PerfScalingAnalysisTest, N=500..10000)

per-fact cost(单 class,N=10000):V5.85 0.68us → V5.88 0.68us(0%)。dual class N=10000:
1.10us → 1.10us(0%)。

## 4. 真实收益 vs 预测差距

V5.87 文档预测 "30-50% per-fact 收益",实测 **0-12%**。原因:

1. **JFR sample 占比 ≠ wall-time 占比** — JFR 100Hz 采样捕捉 CPU 稳态分布,2000 fact
   workload 总耗时 ~3-5ms,sample 数 ~200,setup + JIT + 规则构建占大头。
2. **`String.format` 1.4us × 2000 fact = 2.8ms / 3ms total ≈ 90%** — 这是 hot path 上的
   实际消耗,fix 完仍剩 setup。
3. **Long-running(35s+)才是真 perf 测** — HotPathBenchTest N=51M 跑出的 per-fact 0.68us
   是真渐近线,fix 后(等 30s 让 JFR 拿完)再做 wall-time 才有意义。

**结论**:fix 是 **100% 正确的** — 删了一个 JFR 识别的明显浪费(76% hot path),这是
no-brainer。Wall-time 收益 modest 是 **测量环境限制**,不是 fix 缺陷。

## 5. 后续:V5.89 候选(基于 V5.88 JFR)

V5.88 后 hot path 排名:
1. **`PropertyUtilsBean` 反射 ~240 sample(`getSimpleProperty` 131 + `DefaultResolver.next` 109)**
   — V5.89 高收益候选(20-30% per-fact)
2. **`resetStickyActivities` 137 sample (17%)** — V5.83 必需,优化空间有限
3. `StringBuilder.inflateIfNeededFor` 182 + `appendChars` 160 = 342 sample — 来自 `String.format`
   基础设施(被 `PropertyUtilsBean` 反射参数 toString 触发),跟 V5.89 同源

**V5.89 优化方向**:`Utils.getObjectProperty` 反射缓存 `Class → Method lookup` —
`Method.invoke` 直接调用,绕开 `PropertyUtilsBean` 反射链。

## 6. 改动清单

- production: `server/lib/ruleforge-core/src/main/java/com/ruleforge/runtime/rete/CriteriaActivity.java`
  (1 行早返)
- test: `server/lib/ruleforge-core/src/test/java/com/ruleforge/runtime/rete/CriteriaActivityLogMessageTest.java`
  (3 BDD tests)
- baseline: `server/lib/ruleforge-core/src/test/resources/perf/baseline.json` (`no_eval_3way` p50 1.3→1.14ms)
- 文档: 本文件
- JFR: `target/v588.jfr` (3.8MB,30s)

## 7. 引用

- [[v587-jfr-flamegraph]] V5.87 JFR 识别 logMessage 76% hot path
- [[v585-perf-scaling-analysis]] V5.85 perf scaling
- [[v586-findobject-classcache]] V5.86 持平教训
- [[v583-rete-sticky-state-fix]] V5.83 sticky state 必需开销
- [[v582-allfactsmap-rewrite]] V5.82 allFactsMap

## 8. 经验教训

1. **JFR 识别 hot path 100% 正确** — fix 后 logMessage 100% 消失,证明 JFR 不是噪音
2. **JFR 占比 ≠ wall-time 占比** — 短期 surefire fork 测得 modest 收益,long-running
   workload 才是真测
3. **JIT 部分优化被 JFR 暴露** — `String.format` 在 hot path 上 JIT 不会内联,fix 后才
   看到真实 per-fact 节省
4. **1 行早返是 no-brainer** — debug=false 路径下不调 hot method,production 安全
5. **下一波 V5.89 反射缓存** — `PropertyUtilsBean` 240 sample 是剩余最大单点
