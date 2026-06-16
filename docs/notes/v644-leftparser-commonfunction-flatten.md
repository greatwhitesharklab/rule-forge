# V6.4 — `LeftParser.buildCommonFunctionLeftPart` 2-level nested do-while → enhanced for + 2 个 continue (TD-19.5.6)

## Context

V5.96 22 文件 var123 cleanup 时, `LeftParser.buildCommonFunctionLeftPart:109-141` 的
2-level nested do-while find-first 被显式 skip ("build-time 调用 + private 方法 +
无 characterization test 覆盖不能安全重写 state machine")。 V6.0 立的 "重新审计内层
独立性" 原则 + V6.3 KnowledgeBase 3-level do-while flatten 经验 + 写 characterization
test 锁契约 = V6.4 安全 flatten 路径。

### V6.4 修复前 (2-level nested do-while):

```java
Iterator var3 = element.elements().iterator();

while (true) {
    Element ele;
    do {                                      // level 1: outer
        Object obj;
        do {                                  // level 2: inner
            if (!var3.hasNext()) {
                return part;
            }
            obj = var3.next();
        } while (!(obj instanceof Element));  // skip non-Element items
        ele = (Element) obj;
    } while (!ele.getName().equals("function-parameter"));  // skip non-Parameter Elements
    // process matching
    CommonFunctionParameter p = new CommonFunctionParameter();
    p.setName(ele.attributeValue("name"));
    p.setProperty(ele.attributeValue("property-name"));
    p.setPropertyLabel(ele.attributeValue("property-label"));
    for (Object object : ele.elements()) {    // collect value sub-Element
        if (object instanceof Element) {
            Element e = (Element) object;
            if (e.getName().equals("value")) {
                p.setObjectParameter(this.valueParser.parse(e));
            }
        }
    }
    part.setParameter(p);                     // single-writer last-wins
}
```

### V6.4 修复后 (enhanced for + 2 个 continue):

```java
for (Object obj : element.elements()) {
    if (!(obj instanceof Element)) {
        continue;                              // skip non-Element
    }
    Element ele = (Element) obj;
    if (!ele.getName().equals("function-parameter")) {
        continue;                              // skip non-Parameter
    }
    CommonFunctionParameter p = new CommonFunctionParameter();
    p.setName(ele.attributeValue("name"));
    p.setProperty(ele.attributeValue("property-name"));
    p.setPropertyLabel(ele.attributeValue("property-label"));
    for (Object object : ele.elements()) {
        if (object instanceof Element) {
            Element e = (Element) object;
            if (e.getName().equals("value")) {
                p.setObjectParameter(this.valueParser.parse(e));
            }
        }
    }
    part.setParameter(p);                      // single-writer last-wins 保留
}

return part;
```

**关键等价性证明**:

1. **iterator 状态一致**: 两种写法都按 `element.elements()` List 顺序遍历, 
   `var3.next()` 和 enhanced for 都推进同一个 List iterator 状态。 第 i 次 "process
   matching" 一定看到原代码第 i 次 "find next matching" 找到的 element。

2. **filter 顺序一致**: 原代码 2-level do-while 实际是 AND 短路 (skip non-Element
   OR skip non-function-parameter); V6.4 用 2 个 `continue` 实现相同 AND 短路 —
   `if (!(obj instanceof Element)) continue;` 跳过 non-Element, `if (!ele.getName()
   .equals("function-parameter")) continue;` 跳过 non-Parameter Element。

3. **single-writer 契约保留**: 每次匹配都创建新 `CommonFunctionParameter p`, 
   `part.setParameter(p)` 每次都覆盖。 原代码也是 same behavior, 两种写法 single-
   writer last-wins 100% 等价。

4. **value 子 Element 处理一致**: 内嵌 `for (Object object : ele.elements())` 跟
   原 V5.96 写法 100% 一致 (该内层 V5.96 doc 显式 skip — 无需再改, V5.96 已收口)。

5. **本方法是 parse/ 包的内部 helper**: V6.4 改的是 private 方法, 不影响外部 API
   契约 — `LeftParser.parse(Element)` 公开 API 行为不变。

## 改动

### 文件 1: `LeftParser.java` (1 改动 + 1 import 删除)

**Before** (V6.3):
```java
import java.util.Iterator;
...
private CommonFunctionLeftPart buildCommonFunctionLeftPart(Element element) {
    CommonFunctionLeftPart part = new CommonFunctionLeftPart();
    part.setName(element.attributeValue("function-name"));
    part.setLabel(element.attributeValue("function-label"));
    Iterator var3 = element.elements().iterator();

    while (true) {
        Element ele;
        do {
            Object obj;
            do {
                if (!var3.hasNext()) {
                    return part;
                }
                obj = var3.next();
            } while (!(obj instanceof Element));
            ele = (Element) obj;
        } while (!ele.getName().equals("function-parameter"));

        CommonFunctionParameter p = new CommonFunctionParameter();
        p.setName(ele.attributeValue("name"));
        p.setProperty(ele.attributeValue("property-name"));
        p.setPropertyLabel(ele.attributeValue("property-label"));
        for (Object object : ele.elements()) {
            if (object instanceof Element) {
                Element e = (Element) object;
                if (e.getName().equals("value")) {
                    p.setObjectParameter(this.valueParser.parse(e));
                }
            }
        }
        part.setParameter(p);
    }
}
```

**After** (V6.4):
```java
private CommonFunctionLeftPart buildCommonFunctionLeftPart(Element element) {
    CommonFunctionLeftPart part = new CommonFunctionLeftPart();
    part.setName(element.attributeValue("function-name"));
    part.setLabel(element.attributeValue("function-label"));

    for (Object obj : element.elements()) {
        if (!(obj instanceof Element)) {
            continue;
        }
        Element ele = (Element) obj;
        if (!ele.getName().equals("function-parameter")) {
            continue;
        }
        CommonFunctionParameter p = new CommonFunctionParameter();
        p.setName(ele.attributeValue("name"));
        p.setProperty(ele.attributeValue("property-name"));
        p.setPropertyLabel(ele.attributeValue("property-label"));
        for (Object object : ele.elements()) {
            if (object instanceof Element) {
                Element e = (Element) object;
                if (e.getName().equals("value")) {
                    p.setObjectParameter(this.valueParser.parse(e));
                }
            }
        }
        part.setParameter(p);
    }

    return part;
}
```

- `import java.util.Iterator;` 删除 (不再用 raw `Iterator`)

### 文件 2 (新 BDD): `LeftParserBuildCommonFunctionLeftPartTest.java` (8 tests, `@Nested` + Gherkin `@DisplayName`)

锁 V6.4 修法 (2-level do-while → enhanced for + 2 continue) 的行为不变性:
- `BasicAttributes.emptyCommonFunctionPart`: 无 function-parameter children → part 有 name/label, parameter 为 null
- `SingleFunctionParameter.singleFunctionParameterWithValue`: 单 function-parameter with value 子 Element → parameter 全字段装上, value parsed
- `SingleFunctionParameter.singleFunctionParameterWithoutValue`: 单 function-parameter no value → parameter 装上, objectParameter 为 null
- `MultiFunctionParameterSingleWriter.threeFunctionParametersLastWins`: 3 function-parameter → part.parameter 是 LAST (single-writer last-wins 契约保留)
- `FilterNonMatching.mixedChildrenFilterToFunctionParameter`: 混合 (other-Element + function-parameter) → 只处理 function-parameter
- `FilterNonMatching.noMatchingElementProducesNullParameter`: 全 non-function-parameter Element children → part.parameter 为 null
- `NameMatchIsExact.nameWithPrefixIsNotMatched`: "function-parameters" → skip, 验证 equals 不是 startsWith
- `NameMatchIsExact.nameWithSubstringIsNotMatched`: "common-function-parameter" → skip, 验证 equals 不是 contains

## Verification

### Step 1 — BDD + 全量回归
```bash
mvn test -pl lib/ruleforge-core -Dtest=LeftParserBuildCommonFunctionLeftPartTest
mvn test -pl lib/ruleforge-core
```

- BDD: 8/8 pass (锁 V6.4 修法行为不变性)
- 全量: **721/721 pass** (was 713 → +8 BDD tests), 零 regression

### Step 2 — JFR 信号验证

V6.4 是 build-time 调用 (per-DRL-parse, 不是 per-fact hot path), JFR 0 sample。 本 PR
**0 perf 信号预期**, 跟 V6.3 (KnowledgeBase 3-level do-while flatten) 同档 "code
quality 收口"。

## 复用现有 utility / 模式

- 完全沿 V6.3 立的 "3-level do-while find-first → enhanced for + N continue" 模式
  (V6.3 KnowledgeBase 经验直接套到 V6.4 2-level LeftParser)
- 0 新工具, 0 新 API, 纯 2-level do-while → enhanced for + 2 continue 化简
- 跟 V6.0 AndBuilder.java:66 inner + V6.2 AbstractActivity.visitPaths dead-code
  + V6.3 KnowledgeBase 3-level flatten 同档 (反编译 artifact closure)

## Skip 维持 (V5.96 / V6.0 / V6.1 / V6.2 / V6.3 立的 scope 外)

- `AndBuilder.buildCriterion` 外层 `Iterator var7` state machine — 跟本 PR 无关
- `KnowledgeSessionImpl` labeled loops (3 places) — 跟本 PR 无关
- `LeftParser` 其它方法 (`buildVariableLeftPart` / `buildFunctionLeftPart` /
  `buildMethodLeftPart`) — V5.96 已收口

## 风险 / 已知 trade-off

1. **iterator 状态等价性**: 两种写法都按 List 顺序遍历, iterator 状态一致
   (JDK 语义保证)。
2. **single-writer 契约保留**: 现有 `setParameter` 每次覆盖的 bug-prone 设计
   100% 保留 (不在本 PR scope — 这是业务 bug, V5.96 doc 显式 skip, 未来 V6.5+ 候选
   改成 `parameters` 列表累积)。
3. **JFR 0 sample**: 本方法是 build-time per-DRL-parse 调用, 不在 rete hot path。
   V6.4 是 pure code elegance closure, 跟 V5.96/V6.0/V6.2/V6.3 同档。
4. **`LeftParser.parse` 公开 API 行为不变**: V6.4 改的是 private helper, public
   `parse(Element)` 的行为契约 (按 type 路由到不同 buildXxx) 100% 保留 — 8 BDD
   tests 通过 + 全量 721 pass 证明。
5. **post-switch arithmetic loop 未动**: `LeftParser.parse` 第 72-84 行的 arithmetic
   处理循环已经是 V5.96 优化后 (无 raw `Iterator`), 跟 V6.4 修复无关, 保留。

## 引用

- [[v596-var123-cleanup]] V5.96 立 "零反编译 var123" 原则 (LeftParser buildCommonFunctionLeftPart skip)
- [[v600-andbuilder-var123]] V6.0 立 "重新审计内层独立性" 原则
- [[v611-andactivity-doublelookup]] V6.1 AndActivity.enter 双 lookup
- [[v622-abstractactivity-deadcode]] V6.2 AbstractActivity.visitPaths dead-code
- [[v633-knowledgebase-dowhile-flatten]] V6.3 KnowledgeBase 3-level do-while flatten (V6.4 直接套用其模式)
- 未来 V6.5+ 候选: HashMap 2-array merge / KnowledgeSessionImpl labeled loop
  characterization test / setParameter → parameters 列表累积 (改 single-writer
  契约, 业务 bug fix)
