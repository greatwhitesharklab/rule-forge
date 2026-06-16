# V6.3 — `KnowledgeBase.getKnowledgePackage` 3-level nested do-while → enhanced for + 2 个 continue (TD-19.5.5)

## Context

V5.96 22 文件 var123 cleanup 时, `KnowledgeBase.getKnowledgePackage` 的 3-level nested
do-while (line 38-62) 被显式 skip ("3-level state machine + 内嵌 for, 中间状态机")。
V6.0 立的 "重新审计内层独立性" 原则 (V6.0 doc 锁定) 重新审计: 3-level 状态机实际是
**find-first pattern** (找下一个满足 "name==参数 AND variables!=null AND !variables.isEmpty()"
的 category), 可化简为 enhanced for + 2 个 continue + 1 个 inner for — **无 label /
无 skip-pattern / 无 early return**, 纯 find-first + filter, 1:1 套 V5.96 doc skip
表里 skip-pattern 的反转 (反向收口)。

### V6.3 修复前 (3-level nested do-while + raw List 强转):

```java
Iterator<VariableCategory> var4 = variableCategories.iterator();

while (true) {
    List variables;
    do {                                          // level 1: outer
        do {                                      // level 2: middle
            VariableCategory category;
            String name;
            do {                                  // level 3: inner
                if (!var4.hasNext()) {
                    return this.knowledgePackage;
                }
                category = var4.next();
                name = category.getName();
                variableCategoryMap.put(name, category.getClazz());
            } while (!name.equals("参数"));         // skip non-"参数" categories
            variables = category.getVariables();
        } while (variables == null);               // skip categories with null variables
    } while (variables.isEmpty());                 // skip categories with empty variables

    for (Object variable : variables) {            // process matching
        Variable var = (Variable) variable;
        parameters.put(var.getName(), var.getType().name());
    }
}
```

### V6.3 修复后 (enhanced for + 2 个 continue + inner for):

```java
for (VariableCategory category : variableCategories) {
    String name = category.getName();
    variableCategoryMap.put(name, category.getClazz());
    if (!PARAM_CATEGORY.equals(name)) {
        continue;
    }
    List<Variable> variables = category.getVariables();
    if (variables == null || variables.isEmpty()) {
        continue;
    }
    for (Variable var : variables) {
        parameters.put(var.getName(), var.getType().name());
    }
}

return this.knowledgePackage;
```

**关键等价性证明**:

1. **iterator 状态一致**: 两种写法都按 List 顺序遍历 categories, `var4.next()` 和
   enhanced for 都推进同一个 List iterator 状态。 第 i 次 "process matching" 一定
   看到原代码第 i 次 "find next matching" 找到的 category。

2. **side effect 一致**: 每次 iterator step 都先 `variableCategoryMap.put(name, clazz)`
   (在 `name.equals("参数")` check 之前), 两种写法都保留这个顺序。 所有 category
   都进 variableCategoryMap, 跟原行为一致。

3. **filter 顺序一致**: 原代码 3-level do-while 实际是 AND 短路 (skip non-参数
   OR skip null OR skip empty); V6.3 用 2 个 `continue` 实现相同 AND 短路 —
   `if (!name.equals("参数")) continue;` 跳过 non-参数, `if (variables == null ||
   variables.isEmpty()) continue;` 跳过 null+empty, 剩下才是 matching。

4. **`for (Object variable : variables) { Variable var = (Variable) variable; ... }`**
   → `for (Variable var : variables) { ... }`: 原来 `variables` 是 raw `List`
   (decompiler artifact, `VariableCategory.getVariables()` 实际返 `List<Variable>`),
   强转 `(Variable) variable` 是为了配合 raw type。 V6.3 改回 parameterized type,
   砍掉 1 行强转, 跟 V5.96 ActivationImpl / BuildContextImpl 同 raw List 强转 → parameterized 收口模式。

5. **`PARAM_CATEGORY = "参数"` 提为常量**: 跟 `VariableCategory.PARAM_CATEGORY`
   重复 (那里也是 `"参数"` 常量), 但本方法 self-contained 不依赖 VariableCategory
   内部常量 (避免环依赖风险), 提 local constant 比 `"参数"` magic string 优雅。

## 改动

### 文件 1: `KnowledgeBase.java` (1 改动, 25 行 → 19 行有效)

**Before** (V6.2):
```java
public KnowledgePackage getKnowledgePackage() {
    if (this.knowledgePackage != null) {
        return this.knowledgePackage;
    } else {
        this.knowledgePackage = new KnowledgePackageImpl();
        this.knowledgePackage.setRete(this.rete);
        Map<String, String> variableCategoryMap = new HashMap<>();
        this.knowledgePackage.setVariableCategoryMap(variableCategoryMap);
        List<VariableCategory> variableCategories = this.resourceLibrary.getVariableCategories();
        Map<String, String> parameters = new HashMap<>();
        this.knowledgePackage.setParameters(parameters);
        Iterator<VariableCategory> var4 = variableCategories.iterator();

        while (true) {
            List variables;
            do {
                do {
                    VariableCategory category;
                    String name;
                    do {
                        if (!var4.hasNext()) {
                            return this.knowledgePackage;
                        }

                        category = var4.next();
                        name = category.getName();
                        variableCategoryMap.put(name, category.getClazz());
                    } while (!name.equals("参数"));

                    variables = category.getVariables();
                } while (variables == null);
            } while (variables.isEmpty());

            for (Object variable : variables) {
                Variable var = (Variable) variable;
                parameters.put(var.getName(), var.getType().name());
            }
        }
    }
}
```

**After** (V6.3):
```java
public KnowledgePackage getKnowledgePackage() {
    if (this.knowledgePackage != null) {
        return this.knowledgePackage;
    }

    this.knowledgePackage = new KnowledgePackageImpl();
    this.knowledgePackage.setRete(this.rete);
    Map<String, String> variableCategoryMap = new HashMap<>();
    this.knowledgePackage.setVariableCategoryMap(variableCategoryMap);
    List<VariableCategory> variableCategories = this.resourceLibrary.getVariableCategories();
    Map<String, String> parameters = new HashMap<>();
    this.knowledgePackage.setParameters(parameters);

    for (VariableCategory category : variableCategories) {
        String name = category.getName();
        variableCategoryMap.put(name, category.getClazz());
        if (!PARAM_CATEGORY.equals(name)) {
            continue;
        }
        List<Variable> variables = category.getVariables();
        if (variables == null || variables.isEmpty()) {
            continue;
        }
        for (Variable var : variables) {
            parameters.put(var.getName(), var.getType().name());
        }
    }

    return this.knowledgePackage;
}
```

- `import java.util.Iterator;` 删除 (不再用 raw `Iterator`)
- 加 `private static final String PARAM_CATEGORY = "参数";` 提常量

### 文件 2 (新 BDD): `KnowledgeBaseGetKnowledgePackageTest.java` (10 tests, `@Nested` + Gherkin `@DisplayName`)

锁 V6.3 修法的行为不变性:
- `EmptyCategories`: empty variableCategories → 返 KP, 两个 map 都空
- `NonParamCategory`: name != "参数" → variableCategoryMap 有, parameters 跳
- `ParamWithNullOrEmptyVariables.paramWithNullVariablesSkippedForParameters`: "参数" + null → parameters 跳
- `ParamWithNullOrEmptyVariables.paramWithEmptyVariablesSkippedForParameters`: "参数" + empty → parameters 跳
- `ParamWithNonEmptyVariables.paramWithNonEmptyVariablesPopulatesParameters`: "参数" + 2 variables → parameters 含 entries
- `MultiCategoryMixed.mixedCategoriesFilterCorrectly`: 4 categories 混合 → variableCategoryMap 全收, parameters 只收匹配的
- `MultiParamCategory.twoParamCategoriesBothContribute`: 2 个 "参数" category → 都贡献 parameters
- `CacheReturnsSameInstance.secondCallReturnsCachedInstance`: 多次调用返同 instance
- `NameMatchIsExact.nameWithSubstringIsNotMatched`: "全局参数" (含 "参数" 子串) → parameters 跳 (验证 equals 不是 contains)
- `NameMatchIsExact.nameWithPrefixIsNotMatched`: "参数表" (startsWith "参数") → parameters 跳 (验证 equals 不是 startsWith)

## Verification

### Step 1 — BDD + 全量回归
```bash
mvn test -pl lib/ruleforge-core -Dtest=KnowledgeBaseGetKnowledgePackageTest
mvn test -pl lib/ruleforge-core
```

- BDD: 10/10 pass (锁 V6.3 修法行为不变性)
- 全量: **713/713 pass** (was 703 → +10 BDD tests), 零 regression

### Step 2 — JFR 信号验证

V6.3 是 build-time 调用 (per-KnowledgePackage, 不是 per-fact hot path), JFR 0 sample。
本 PR **0 perf 信号预期**, 跟 V6.0 (var123 收尾) + V6.2 (dead-code 收尾) 同档
"code quality 收口"。

## 复用现有 utility / 模式

- 完全沿 V5.96 立 "零反编译 var123 + 零反编译 dead branch" 原则
- 0 新工具, 0 新 API, 纯 3-level do-while → enhanced for + 2 continue 化简
- 跟 V6.0 AndBuilder.java:66 内层 + V6.2 AbstractActivity.visitPaths dead-code
  收口同档 (反编译 artifact closure)

## Skip 维持 (V5.96 / V6.0 / V6.1 / V6.2 立的 scope 外)

- `AndBuilder.buildCriterion` 外层 `Iterator var7` state machine — 跟本 PR 无关
- `LeftParser.buildCommonFunctionLeftPart` find-first — 跟本 PR 无关
- `KnowledgeSessionImpl` labeled loops — 跟本 PR 无关
- `KnowledgeBase` 其它方法 (getRete/getResourceLibrary) — 无反编译 artifact

## 风险 / 已知 trade-off

1. **iterator 状态等价性**: 两种写法都按 List 顺序遍历, iterator 状态一致
   (JDK 语义保证)。
2. **side effect 顺序**: `variableCategoryMap.put(name, clazz)` 在 `name.equals`
   check 之前, 两种写法都保留 — 所有 category 都进 variableCategoryMap。
3. **JFR 0 sample**: 本方法是 build-time 调用, 不在 rete hot path。 V6.3 是 pure
   code elegance closure, 跟 V5.96 22 文件主 cleanup 一档, 不期望 perf 突破。
4. **PARAM_CATEGORY 常量**: 跟 `VariableCategory.PARAM_CATEGORY` 重复, 但本方法
   self-contained 不依赖 VariableCategory 内部常量 (避免环依赖), 提 local constant
   比 `"参数"` magic string 优雅。

## 引用

- [[v596-var123-cleanup]] V5.96 立 "零反编译 var123" 原则 (3-level do-while skip)
- [[v600-andbuilder-var123]] V6.0 var123 收尾 + 重新审计内层独立性原则
- [[v611-andactivity-doublelookup]] V6.1 AndActivity.enter 双 lookup
- [[v622-abstractactivity-deadcode]] V6.2 AbstractActivity.visitPaths dead-code
- 未来 V6.4+ 候选: HashMap 2-array merge / LeftParser find-first /
  KnowledgeSessionImpl labeled loop characterization test
