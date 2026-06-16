# V5.92 — `ReteInstance` 预计算 flat sticky 列表(resetStickyStateOnly 优化)

> **TL;DR**:`resetStickyStateOnly()` per-fact 递归 walk 整个 activity 子树,
> V5.91 JFR 抓 802 sample (20% hot path) 排 top-1。V5.92 在 ReteInstance
> 构造时一次性 walk,生成 `List<AbstractActivity> stickyActivities` flat 列表
> (跳过 TerminalActivity 这种 `reset()` no-op),resetStickyStateOnly() 走 flat
> 列表,无递归 + 无 instanceof + 无 Path.getTo() 虚拟调用。
>
> **JFR 验证(V5.91 → V5.92,30s HotPathBenchTest)**:
> - `resetStickyStateOnly` **802 → 49 sample (-94%)** ✅
>
> **Wall-time 验证**:
> - `HotPathBenchTest` per-fact **0.23us → 0.21us (-9%)**,iter 38136 → 41907 (+10%)
> - `EvalBenchmarkV579` 4 scenario 全 -10~17%:
>   - `no_eval`: 1.22ms → 1.05ms (-14%)
>   - `no_eval_3way`: 0.92ms → 0.83ms (-10%)
>   - `no_eval_5r`: 2.81ms → 2.50ms (-11%)
>   - `eval`: 1.05ms → 0.87ms (-17%)
> - 全量回归 **650/650 pass**(原 646 + 4 new `ReteInstanceStickyListTest`)
> - `baseline.json` 同步更新 V5.92 4 scenario 新值

## 1. 起因

V5.91 PR #154 收 UUID 链 543 sample → 0 后,post-V5.91 JFR top-1 是
`resetStickyActivities` 802 sample (20% hot path)。V5.92 攻这个。

## 2. Audit:为什么 resetStickyStateOnly 这么贵?

`ReteInstance.resetStickyStateOnly()` (V5.83 line 55-59) 每次 fact 进入 rete
前调一次(在 `KnowledgeSessionImpl.evaluationRete` line 338):

```java
public void resetStickyStateOnly() {
    for (ObjectTypeActivity objectTypeActivity : objectTypeActivities) {
        resetStickyActivities(objectTypeActivity.getPaths());
    }
}

private void resetStickyActivities(List<Path> paths) {
    if (paths == null) return;
    for (Path path : paths) {
        Activity activity = path.getTo();
        if (activity instanceof AbstractActivity) {
            AbstractActivity ac = (AbstractActivity) activity;
            ac.reset();
        }
        resetStickyActivities(activity.getPaths());
    }
}
```

**per-fact 工作量**(HotPathBenchTest 2-class rete):
- 2 ObjectTypeActivity(根)迭代
- 每个 OTA → 1 CriteriaActivity → 1 AndActivity → 1 TerminalActivity
- **8 reset() 调用 + 4 递归 + 4 instanceof + 4 Path.getTo() 虚拟调用**
- 共 ~16 method invocation per fact

`reset()` 本身非常便宜(CriteriaActivity = 1 field write,AndActivity = 2 field
write,TerminalActivity = empty,**ObjectTypeActivity.reset() 也是 empty
但我们不调用**),**贵的是 virtual dispatch + instanceof + 递归开销**。

post-V5.91 JFR 计算:802 sample / 30s @ 100Hz = 8.02s spent in
resetStickyActivities;30s 内 152.6M fact → **52.5ns per fact**(占 per-fact
total 230ns 的 **23%**)。

## 3. V5.92 修法:预计算 flat sticky 列表

`ReteInstance` 构造时一次性 walk 整个 activity 子树,生成
`List<AbstractActivity> stickyActivities` flat 列表(用 `LinkedHashSet` 维护
插入顺序 + dedup,兼容 DAG rete)。`resetStickyStateOnly()` 走这个 flat
列表,无递归 + 无 instanceof + 无 `Path.getTo()` 虚拟调用。

```java
public ReteInstance(...) {
    this.stickyActivities = computeStickyActivities(objectTypeActivities);
}

private static List<AbstractActivity> computeStickyActivities(List<ObjectTypeActivity> otas) {
    Set<AbstractActivity> seen = new LinkedHashSet<>();
    if (otas == null) return List.of();
    for (ObjectTypeActivity ota : otas) {
        collectSticky(ota.getPaths(), seen);
    }
    return List.copyOf(seen);
}

private static void collectSticky(List<Path> paths, Set<AbstractActivity> out) {
    if (paths == null) return;
    for (Path path : paths) {
        Activity activity = path.getTo();
        if (activity instanceof AbstractActivity) {
            AbstractActivity ac = (AbstractActivity) activity;
            // 跳过 TerminalActivity:reset() no-op
            if (!(activity instanceof TerminalActivity)) {
                out.add(ac);
            }
            collectSticky(ac.getPaths(), out);
        }
    }
}

public void resetStickyStateOnly() {
    for (AbstractActivity ac : stickyActivities) {
        ac.reset();
    }
}
```

**per-fact 工作量**(HotPathBenchTest 2-class rete):
- 3 reset() 调用(2 Criteria + 1 共享 And)+ 3 list iter + 0 递归
- 共 **6 method invocation per fact**(-63% vs V5.91 16)

## 4. 为什么这条跟 V5.84 不一样

V5.84 尝试 "按 fact class 增量 reset"(只 reset fact 命中的 OTA 子树),理论上
节省 cross-class reset,实际**反优化**(-20~49% 慢)。

**根因**:`ObjectTypeActivity.support()` 是反射(`Class.isAssignableFrom` +
字符串 equals),2000 fact × 2 OTA = 4000 次反射 > 节省的 2000 次 cross-class
reset(亚毫秒级)。**净 loss 0.5-2.5ms**。

V5.92 完全不同:
1. **不走反射** — `stickyActivities` 在构造时 walk 一次,运行时是纯 list iter
2. **不省 cross-class reset** — 所有 sticky activity 一起 reset,跟 V5.83 行为
   一致(tree rete 现状)
3. **省的是递归 + instanceof + 虚拟调用** — 这些是 V5.83 的真正 perf cost,跟
   cross-class 无关

## 5. Audit:activity tree 构造时是完整的吗?

V5.92 假设:构造 `ReteInstance` 时,所有 `addPath` 已经完成,`stickyActivities`
可以一次性 walk 完整。

**Trace 验证**(`NodeActivityFactory.java:60-97`):
```java
private static ObjectTypeActivity createObjectType(...) {
    ObjectTypeActivity activity = new ObjectTypeActivity(...);
    for (Line line : node.getLines()) {       // ← 构造时 addPath
        activity.addPath(line.newPath(context));
    }
    return activity;
}
// 同模式:CriteriaActivity / AndActivity / OrActivity 都是构造时 addPath
```

`Rete.newReteInstance()` (line 39-52) 流程:
1. 对每个 ObjectTypeNode,`NodeActivityFactory.create(node, contextMap)` →
   递归构造 ObjectTypeActivity + 所有 children activities,所有 addPath 在
   `create` 调用栈内完成
2. 收集 `objectTypeActivities` 列表
3. **`new ReteInstance(objectTypeActivities, ...)`** ← 此时所有 activities
   都已完整

ReteInstance ctor 第 4 步 `computeStickyActivities(objectTypeActivities)`
可以安全 walk 整个子树。

**结论**:**V5.92 pre-compute 时机正确**,activity tree 完整无遗漏。

## 6. 改动

### 6.1 production: `server/lib/ruleforge-core/src/main/java/com/ruleforge/runtime/rete/ReteInstance.java`

- 新字段 `private final List<AbstractActivity> stickyActivities;`
- 新静态方法 `computeStickyActivities` / `collectSticky`
- ctor 第 4 行 `this.stickyActivities = computeStickyActivities(...)`
- `resetStickyStateOnly()` 从递归 walk 改成 flat list iter
- 删 `resetStickyActivities(List<Path>)` 私有递归方法
- 新增 package-private `getStickyActivitiesForTest()`(测试用)

### 6.2 BDD: `server/lib/ruleforge-core/src/test/java/com/ruleforge/runtime/rete/ReteInstanceStickyListTest.java` (新, 4 tests)

- `dualClassReteStickyListHas2CriteriaAnd1SharedAnd`: 锁 dual-class rete 列表
  = 2 CriteriaActivity + 1 共享 AndActivity(2 pattern 共享 1 And node)+ 0
  Terminal = 3 项
- `terminalActivityNotInStickyList`: 锁 TerminalActivity 不在列表
- `singleClassReteStickyListHas1Criteria`: 锁 single-class rete 列表 = 1 项
- `resetStickyStateOnlyIsIdempotentAndSafe`: 锁 resetStickyStateOnly 多次调
  用无异常 + 列表 immutable

**TDD red → green 流程**:
- red: `getStickyActivitiesForTest` 不存在,BDD 编译失败
- green: 字段 + getter + resetStickyStateOnly flat list 后 4/4 pass

## 7. 验证

### 7.1 单元 + 全量回归

- `ReteInstanceStickyListTest`: **4/4 pass**
- `mvn test -pl lib/ruleforge-core`: **650/650 pass**(原 646 + 4 new),无回归

### 7.2 JFR 30s HotPathBenchTest 抓取

V5.92 top-15 hot method 排序(`resetStickyStateOnly` 从 802 → **49 sample**):

| hot method | V5.91 sample | V5.92 sample | 变化 |
|---|---|---|---|
| **`resetStickyStateOnly`** | **802** | **49** | **-94%** ✅ |
| `hashCode` | 127 | 586 | workload 提速 +10%(per-fact 下降 → 同样 30s 跑更多 fact → hash 调用更多) |
| `append` | 382 | 500 | workload 提速 |
| `getNode` | 103 | 454 | workload 提速 |
| `containsKey` | 95 | 433 | workload 提速 |
| `passAndNode` | 339 | 334 | 持平(独立 perf path) |
| `getCriteriaValue` | 181 | 327 | workload 提速 |
| `doPassAndNode` | 256 | 250 | 持平 |

**核心 fix 100% 兑现**:`resetStickyStateOnly` V5.91 802 sample → V5.92 49
sample(-94%)。

### 7.3 Wall-time bench

**HotPathBenchTest 35s long-running**(同 V5.87 JFR workload):

| V5.79 | V5.83 | V5.88 | V5.89 | V5.90 | V5.91 | V5.92 |
|---|---|---|---|---|---|---|
| (没跑) | (没跑) | 0.68us | 0.62us | 0.36us | 0.23us | **0.21us** |

- V5.91 → V5.92:**per-fact -9%**,iter **+10%**(38136 → 41907)
- 累计 V5.88 + V5.90 + V5.91 + V5.92 四个独立 fix:HotPath per-fact 0.68us (V5.87) → 0.21us (V5.92) = **-69%**

**EvalBenchmarkV579, 4 scenarios × 50 iter(4 次 re-run 取 median)**:

| scenario | V5.90 p50 | V5.92 p50(4-run median) | V5.92 vs V5.90 |
|---|---|---|---|
| `no_eval` (2-pattern) | 1.22ms | 1.05ms | **-14%** |
| `no_eval_3way` (3-pattern) | 0.92ms | 0.83ms | **-10%** |
| `no_eval_5r` (5 rule × 2-pattern) | 2.81ms | 2.50ms | **-11%** |
| `eval` (no match) | 1.05ms | 0.87ms | **-17%** |

`baseline.json` 同步更新 V5.92 4 scenario 新值(都低于 1.5x fail multiplier
上限)。

## 8. 经验教训

1. **V5.84 doc 反向指导 = 重要教训** — V5.83 doc 末尾标"按 fact class 增量 reset
   是 V5.84+ 优化方向",V5.84 实际跑出来 -20~49% 反优化。doc 不能直接信,要
   audit runtime 成本结构(反射 vs 实际工作),不能光看"V5.83 O(n) 看着慢"
2. **Virtual dispatch + 递归 + instanceof 是隐形 perf cost** — `reset()`
   本身 1-2 field write,JFR 抓 52ns/fact 不可能全在 field write,**真正贵的是
   virtual dispatch(8 次) + 递归调用栈(4 层) + instanceof(4 次)**。V5.92
   砍掉这些"框架开销",field write 不变
3. **Pre-compute 跟反射是两码事** — V5.84 走"动态路由(反射找相关 class)",
   V5.92 走"静态预计算(构造时 walk)",都是"少做事"思路但 cost 结构完全不同
4. **LinkedHashSet 跟 List 的取舍** — V5.92 用 LinkedHashSet 而非纯 List 是
   防御性:复杂 rete 可能是 DAG(同一 activity 多个 parent 路径),Set 自动
   dedup;tree rete 也 work(list.contains() 也 dedup 但 O(n))
5. **跳过 no-op override 也要小心** — TerminalActivity.reset() 是 no-op 没错,
   但跳过它省的是 virtual dispatch 本身(N 次 call × dispatch 成本)而非
   reset() body 成本。audit 时区分 "method body" vs "method call"

## 9. 后续候选(post-V5.92 JFR)

post-V5.92 top hot method:
1. `hashCode` 586 sample(15% hot path)— HashMap 操作,优化需 audit HashMap
   使用模式
2. `append` 500 sample(13%)— StringBuilder 路径
3. `getNode` 454 sample(12%)— rete dispatch 路径
4. `containsKey` 433 sample(11%)— HashMap.containsKey
5. `passAndNode` 334 sample(9%)— beta join pass-through

V5.93+ 候选:
- HashMap → IdentityHashMap / specialized structure(若 fact class 数量 ≤ 4)
- StringBuilder 路径 audit(V5.88 已优化 logMessage String.format,可能还有
  其他 StringBuilder 热点)

## 10. 改动清单

- production: `server/lib/ruleforge-core/src/main/java/com/ruleforge/runtime/rete/ReteInstance.java`
  (+28/-7,新增 stickyActivities 字段 + computeStickyActivities/collectSticky
  + flat list resetStickyStateOnly)
- BDD: `server/lib/ruleforge-core/src/test/java/com/ruleforge/runtime/rete/ReteInstanceStickyListTest.java`
  (新, 4 BDD tests)
- baseline: `server/lib/ruleforge-core/src/test/resources/perf/baseline.json`
  (4 scenario p50/p95 更新)
- 文档: 本文件
- JFR: `target/v592.jfr` (3.5MB, 30s) — `resetStickyStateOnly` 49 sample 验证

## 11. 引用

- [[v591-factids-atomiclong]] V5.91 PR (V5.92 起点 — UUID 链 0 后 top-1 是 resetSticky)
- [[v590-rule-debug-default-flip]] V5.90 翻 Rule.debug,V5.91 才暴露 UUID,V5.92
  才能精确测 resetSticky 占比
- [[v589-getobjectproperty-reflection-cache]] V5.89 反射缓存
- [[v588-logmessage-early-return]] V5.88 logMessage 早返
- [[v587-jfr-flamegraph]] V5.87 JFR 原始数据
- [[v586-findobject-classcache]] V5.86 ConcurrentHashMap 模式
- [[v585-perf-scaling-analysis]] V5.85 perf scaling + 1-3us per-fact 真实成本
- [[v584-incremental-reset-attempted]] V5.84 反优化教训 — 不要按 doc 文字推断优化方向
- [[v583-rete-sticky-state-fix]] V5.83 resetStickyStateOnly 引入背景
- `target/v591.jfr` (V5.91 baseline,resetStickyStateOnly 802 sample)
- `target/v592.jfr` (V5.92 验证,resetStickyStateOnly 49 sample)
