# V6.0 — AndBuilder.java:66 反编译 `Iterator var11` 收尾

## Context

V5.96 22 文件 var123 cleanup 后, core 还剩 6 个 `Iterator varN` 模式:

- `AndBuilder.java:32, 66` (state machine + 简单内层)
- `LeftParser.java:109` (buildCommonFunctionLeftPart find-first)
- `KnowledgeSessionImpl.java:347, 407, 433` (labeled `label42/82/84` 控制流)

V5.96 doc 显式 skip 这些:
- `AndBuilder.buildCriterion` — "do-while + 内嵌 return 提前构造 result,中间状态机"
- `LeftParser.buildCommonFunctionLeftPart` — "skip-pattern"
- `KnowledgeSessionImpl` — class javadoc 明确要求 "无特征化测试覆盖不能重写循环结构"

V6.0 重新审计: 内层 `Iterator var11 = nodes.iterator(); while (var11.hasNext()) { ... }`
跟外层 state machine **解耦** — 内层是 simple iteration(无 label / 无 skip / 无 early return),
可独立 for-each 化。 这是 V5.96 漏掉的 1 行 var123。

## 改动

### 文件: `AndBuilder.java` (1 改动)

**Before** (V5.96):
```java
Iterator var11 = nodes.iterator();
while (var11.hasNext()) {
    BaseReteNode node = (BaseReteNode) var11.next();
    if (node instanceof CriteriaNode) {
        ...
    } else if (node instanceof JunctionNode) {
        ...
    }
}
```

**After** (V6.0):
```java
for (Object obj : nodes) {
    BaseReteNode node = (BaseReteNode) obj;
    if (node instanceof CriteriaNode) {
        ...
    } else if (node instanceof JunctionNode) {
        ...
    }
}
```

`nodes` 是 raw `List` (decompiled 时代码, 没用泛型), 所以 for-each element
类型是 `Object`, 需强转 `BaseReteNode` — 跟 V5.96 ActivationImpl.java / BuildContextImpl.java
同 raw List 强转模式。

## 行为等价性 audit

| 原 V5.96 行为 | V6.0 for-each 行为 | 等价? |
|---|---|---|
| `var11.hasNext()` + `var11.next()` 遍历 List | enhanced for 遍历 List | ✅ 100% 等价 (JDK 语义保证) |
| `(BaseReteNode) var11.next()` 强转 | `(BaseReteNode) obj` 强转 | ✅ 等价 (同一 List, 同一元素) |
| `if (node instanceof CriteriaNode)` 分支 | 同 | ✅ |
| `if (node instanceof JunctionNode)` 分支 | 同 | ✅ |
| 无 label / 无 skip / 无 early return | 同 | ✅ |

外层 `Iterator var7` + `while(true) { do { ... } while(nodes==null) }` state machine
**不动** — 跟 V5.96 一致保留。 `import java.util.Iterator;` 也保留(外层 var7 用)。

## Verification

```bash
mvn test -pl lib/ruleforge-core
```

690/690 pass — 行为 100% 等价, 无 regression。

## Skip rationale (保留未改)

- `AndBuilder.java:32` 外层 `Iterator var7` + `while(true) { do { if (!var7.hasNext()) { ... return; } } while(nodes==null) }`
  — state machine, V5.96 doc skip 维持。
- `LeftParser.java:109` `Iterator var3 + while(true) { do { do { ... } } ... }` — find-first
  pattern, V5.96 skip 维持。 需 BDD test 锁"skip non-Element + skip non-function-parameter"
  行为才安全改。
- `KnowledgeSessionImpl.java:347, 407, 433` — labeled `label42/82/84` + `continue label`
  控制流, class javadoc 显式要求 characterization test 覆盖才能重写。

## 引用

- [[v596-var123-cleanup]] V5.96 22 文件主 cleanup (立 V6.0 候选: AndBuilder 内层)
- V5.96 doc "Skip" 表:
  - `AndBuilder.buildCriterion` — "do-while + 内嵌 return 提前构造 result"
  - `KnowledgeSessionImpl.evaluationRete/activeRule/activeAgendaGroup` — "class javadoc 明确要求 characterization test"
