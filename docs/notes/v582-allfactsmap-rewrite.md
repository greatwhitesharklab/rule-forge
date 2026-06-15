# V5.82 — allFactsMap 改 List<Object> 累加 + 区分 2-pattern join 独立 bug

## 背景

V5.81 调查 `EvalBenchmarkV579` fired=0 时发现 2 个独立 bug(见
[[v581-criteria-test-wiring-fix]]):
- **#1** test wiring bug — V5.81 修 ✓
- **#2** production bug `allFactsMap` className-keyed 覆盖 — 留 V5.82+ (本文件)

V5.82 实际修 #2 时又发现**第三个**独立 bug,2-pattern join 增量 memory 缺陷 —
跟 allFactsMap 无关,是 rete JoinNode 实现的根本问题,留 V5.83+ (TD-19.5.4)。

## V5.82 修法 (TD-19.5.1)

**生产代码改动** `KnowledgeSessionImpl.java`:
- 新字段 `List<Object> allFactsList`(主存)
- `addToFactsMap(fact)` 改 `allFactsList.add(fact)`(累加,不按 className 覆盖)
- 引擎路径(fireRules / activeRule / activeAgendaGroup / retract)走 `allFactsList`
- `retract(fact)` 改 `allFactsList.remove(fact)`(按 reference equality 移除首次)
- `getAllFactsList()` 新方法返全 fact 列表
- `getAllFactsMap()` 改成**惰性 last-wins Map 视图**(遍历 allFactsList 重建)
  - 保留 backward compat:`ValueCompute.findObject` / `LoopRule` /
    `KnowledgeSessionTest:265` 的 `containsEntry("User", entity)` 契约不变
  - 新代码应使用 `getAllFactsList()`

**接口改动** `WorkingMemory.java`:
- 新增 `List<Object> getAllFactsList()` 方法
- `getAllFactsMap()` 加 `@Deprecated` Javadoc 注明"新代码请用 getAllFactsList"

**测试覆盖**:
- `AllFactsMapRetainsAllInsertsTest`(新,3 tests):
  - `thousandInsertsRetained`: 1000 个同 className Foo insert → `getAllFactsList().size()==1000`
    + `getAllFactsMap().size()==1`(last wins 视图保留)
  - `mixedTypesAllRetained`: 500 Foo + 300 Bar → list size 800 + map size 2
  - `fireRulesUsesListNotMap`: 1000 Foo + 1 always-match rule → firedRules=1(锁 engine 路径不 NPE)
- `TwoPatternJoinFiresTest`(新,2 tests): 调查 #3 bug 时引入
  - `twoPatternOneFactEachFires`: 1+1 fact 2-pattern join → fired=1
  - `twoPatternWithNoise`: match-first + 2 noise → fired=1
  - **reverse: noise-first 顺序下 fired=0**(锁 #3 bug 复现)

## V5.82 没收口的 #3 bug (TD-19.5.4,留 V5.83+)

`EvalBenchmarkV579` 修完 allFactsMap 后仍 fired=0。`TwoPatternJoinFiresTest`
锁了 2 个独立表现:
- 2-pattern join 1+1 fact **能 fire** — 引擎 join 节点本身能 match
- 2-pattern join 4 fact(noise-first 顺序)**不能 fire** — JoinNode 不跨 fact 维护
  left/right memory

读 `CriteriaActivity.enter` 源码(规则 24-57)发现 JoinNode 只看当前 `obj`,不持有
"前序 propagation pass 看到的对方 pattern fact 集合"。`ReteInstance.resetForReevaluate`
按 fact class 单独 reset 路径(规则 48-55),没维护 beta-memory。

修法(留给 V5.83+): 给 CriteriaJoinActivity 加 beta-memory 字段 + 增量 reevaluate
时同步维护。可能涉及 `ReteInstance.enter(fact)` 调用链大改,需新 BDD + 大 workload
验证 — 远超 V5.82 TD-19.5 范围。

## EvalBenchmarkV579 4 scenario V5.82 状态

| Scenario | 期望 (V5.46) | V5.79/80/81 实测 | V5.82 实测 | 状态 |
|---|---|---|---|---|
| `no_eval` 3 rule + 1000 P/A | fired=3 | fired=0 (#1+#2) | fired=0 (#3) | **收紧失败** — #3 留 V5.83+ |
| `no_eval_3way` 3-pattern 链 | fired=0(无 cross) | fired=0 | fired=0 | ✓ 本就 fired=0 |
| `no_eval_5r` 5 rule | fired=3 | fired=0 | fired=0 | **收紧失败** — #3 |
| `eval` 字段过滤全 no-match | fired=0 | fired=0 | fired=0 | ✓ 本就 fired=0 |

bench 4 scenario `assertEquals(0, firedRules, ...)` 注释更新指向本文 + #3 bug。
**不收紧** — 等 V5.83+ 修完 #3 才统一收紧。

## Test count

- V5.81 main: 615 非 perf + 5 perf = 620 total
- V5.82: 620 非 perf(含 2 个新 perf-tagged Test 类也跑,默认 test 不排除 tag;
  `-Pperf` 走独立 10 perf)+ 10 perf(AllFactsMap 3 + TwoPattern 2 + DrlRebuild 1
  + EvalBenchmark 4)= 630 total 路径独立,0 failure

## Files changed

| File | +/- | 用途 |
|---|---|---|
| `runtime/WorkingMemory.java` | +14/-2 | 新增 getAllFactsList + @Deprecated getAllFactsMap |
| `runtime/KnowledgeSessionImpl.java` | +29/-16 | 改 allFactsList + getAllFactsMap 惰性视图 |
| `rete/perf/EvalBenchmarkV579.java` | +12/-12 | 注释更新指向 V5.82 docs + #3 bug |
| `rete/perf/AllFactsMapRetainsAllInsertsTest.java` | +150(新) | 锁 1000-facts 累加 + backward compat |
| `rete/perf/TwoPatternJoinFiresTest.java` | +130(新) | 调查 #3 bug 引入 + 锁 noise-first 不 fire |
| `docs/notes/v582-allfactsmap-rewrite.md` | +新增 | 本文件 |

## 后续 V5.83+ (TD-19.5.4)

`CriteriaJoinActivity` 加 beta-memory:
- 左/右 fact 集合字段
- `enter()` 时增量匹配 + 添加
- `reset()` 时清空
- 配合 `ReteInstance.resetForReevaluate(fact)` 按 fact class 单边 reset

修完后:
- `EvalBenchmarkV579` no_eval 收紧到 fired=3
- no_eval_3way 保持 fired=0(无 cross data)
- no_eval_5r 收紧到 fired=3
- eval 保持 fired=0
- baseline.json 同步

## 关键教训

1. **诚实区分独立 bug** — V5.82 修 allFactsMap 后发现 #3 是 rete join 实现问题,
   不是 allFactsMap 的衍生问题。不为 PR narrative 把 #2 和 #3 强行合并。
2. **大 workload 调查要拆最小 BDD** — `TwoPatternJoinFiresTest` 从 1+1 到 4 fact
   拆 3 个 case,定位 "insert 顺序敏感" 是 JoinNode 的问题,不是 fact 数。
3. **看源码不要只跑测试** — 跑测试看到 fired=0 后,直接读 `CriteriaActivity.enter`
   + `ReteInstance.resetForReevaluate` 锁定 #3 是 rete 增量 memory 缺陷,而不是
   V5.78+ regression。
