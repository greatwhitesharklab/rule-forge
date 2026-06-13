---
title: 路线 B 后老 library 资源迁移手册(V5.44.3)
---

# 路线 B 后老 library 资源迁移手册(V5.44.3)

> 适用版本:V5.44.3+(2026-06-13 起)
> 适用对象:从 V5.43 / V5.42 升级到 V5.44.3 的项目,**只有仍在用 .xml library
> 资源**(`<action-library>` / `<variable-library>` / `<constant-library>` /
> `<parameter-library>`)的运维需要看这个

## TL;DR

V5.44.3 删了 4 个老 library deserializer / 4 个 library parser / 4 个
library ResourceBuilder,library 资源**改走 DRL 4 顶层 `import` 段**。
如果你的项目还在用 .xml library,**必须**手工转 DRL import 段,否则
console-app / executor-app 启动会报 NoSuchBeanDefinitionException。

本文给出**手工转换模板** —— V5.45+ 才会补脚本工具,目前(2026-06-13)只
支持手工迁移。

## 为什么改

V5.40-43 路线 B 把决策表/决策树/评分卡/规则集都迁到 DMN 1.3 / PMML 4.4 /
DRL 4 自研 ANTLR4 格式。V5.43.2 删了老 `RuleSetDeserializer`,但**保留**
4 个老 library deserializer 作为 .xml 兜底。V5.44.3 决定:

1. DRL 4 grammar 加 `import` 关键字(见 `DrlLexer.g4:38`),支持顶层
   `import "libs/x.drl";` 段
2. DrlAstVisitor 收集 import 路径到 `DatatypeResolver`(`addImport`)
3. 删 4 个老 library deserializer / 4 个 library parser / 4 个 library
   ResourceBuilder

**好处**:library 文件可以用 DRL `declare` 段统一管理 type,V5.45+ 库
加载器直接 fetch import 路径列表并发加载。

## 怎么查项目是否受影响

```bash
# 在项目根目录找老 .xml library 资源
find . -name "*.library" -type f

# 或在 console-ui 项目资源树里看 fileType 是 ActionLibrary / ConstantLibrary /
# VariableLibrary / ParameterLibrary 的资源
```

如果一个都没有 → **项目不受影响**,跳过本文。

## 转换模板

### 1. `<action-library>` → DRL `declare` 段 + `import` 段

**老格式**(`.actionlib.xml` / `<action-library>` 根):
```xml
<?xml version="1.0" encoding="UTF-8"?>
<action-library>
    <spring-bean id="calcScore" name="calcScoreBean" class="com.x.Calc">
        <method method-name="compute" name="计算评分">
            <parameter name="applicant" type="Applicant"/>
        </method>
    </spring-bean>
</action-library>
```

**新格式**(`.drl`):
```drl
// V5.44.3 — library 走 DRL declare 段;library 文件由 .drl 自身描述。
// 老 .actionlib.xml 里的 <spring-bean> 现在不通过 Spring container 注入,
// 改走规则执行时 classpath 反射调用。V5.45+ 会有 library 加载器自动 fetch
// import 路径列表,届时 this 部分可省略。
import "libs/actions.drl";

declare SpringBean
    @role( action )
    id : String
    class : String
    methods : List
end

rule "registerCalcScore"
    when
        // ... 触发条件由你定义
    then
        // 调用方
end
```

> **实际操作**:V5.44.3 仅 grammar 层面支持 import 段;**library 实际加载
> 留 V5.45+ 跟进**(plan 已锁定)。所以 V5.44.3 阶段,你只要**保留**
> action bean 的 Java class,**不**通过 .xml 注册;运行时通过 classpath
> reflection 调用即可。

### 2. `<variable-library>` → DRL `declare` 段

**老格式**:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<variable-library>
    <var-category name="申请人" clazz="com.x.Applicant">
        <var name="age" type="Integer" label="年龄"/>
        <var name="income" type="Double" label="收入"/>
    </var-category>
</variable-library>
```

**新格式**(`.drl`):
```drl
import "libs/variables.drl";

declare Applicant
    age : Integer
    income : Double
    name : String
    // ... 其他字段
end
```

> 字段名 / type 跟老 `<var>` 一一对应;V5.45+ 加载器会同步 fetch
> `<var-category>` 元数据(name / label)。

### 3. `<constant-library>` → DRL `declare` 段

**老格式**:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<constant-library>
    <const-category name="限额" label="贷款限额">
        <const name="MAX_LOAN" type="Double" value="500000.00" label="最大贷款额"/>
    </const-category>
</constant-library>
```

**新格式**:`globals` 段或者 `declare` + `rule` 初始化:

```drl
import "libs/constants.drl";

global Double MAX_LOAN = 500000.00;
```

### 4. `<parameter-library>` → DRL `declare` 段

**老格式**:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<parameter-library>
    <parameter name="approvedAmt" type="Double" label="批准金额"/>
    <parameter name="approvalDate" type="Date" label="批准日期"/>
</parameter-library>
```

**新格式**:rule 内用 `declare` 或直接 `FunctionDescriptor` 注册:

```drl
import "libs/parameters.drl";

rule "initParams"
    salience 1000
    when
        not Parameter(approvedAmt : Double)
    then
        insert(new Parameter(0.0, null));
end
```

> parameter 实际通过 `FunctionDescriptor` 注册 — V5.44.3 保留现有
> `RuleForgeConsoleApplication` 的 `FunctionDescriptor` 自动注册机制。

## 转换步骤(checklist)

1. **列出所有 .xml library 资源**
   ```bash
   find . -name "*.library" -type f > /tmp/legacy-libraries.txt
   ```

2. **每个 library 创建对应 .drl 文件**
   - `<action-library>` → `libs/actions.drl`(用 `declare SpringBean` 段)
   - `<variable-library>` → `libs/variables.drl`(用 `declare` 段)
   - `<constant-library>` → `libs/constants.drl`(用 `global` 段)
   - `<parameter-library>` → `libs/parameters.drl`(用 `declare` 段 + FunctionDescriptor)

3. **主规则 .drl 顶层加 import 段**
   ```drl
   import "libs/variables.drl";
   import "libs/constants.drl";
   import "libs/actions.drl";
   import "libs/parameters.drl";
   ```

4. **删 .xml library 文件**
   ```bash
   xargs rm < /tmp/legacy-libraries.txt
   ```

5. **跑 mvn test 验证**
   ```bash
   cd server && mvn -pl lib/ruleforge-core,lib/ruleforge-dsl test
   ```

6. **console-app / executor-app 启动验证**
   ```bash
   cd server
   mvn -pl app/ruleforge-console-app -am spring-boot:run
   # 看启动日志,无 NoSuchBeanDefinitionException 即可
   ```

## 排错

| 症状 | 原因 | 修法 |
|---|---|---|
| 启动 `NoSuchBeanDefinitionException: ruleforge.actionLibraryDeserializer` | 删了 bean 后老 XML 资源还在被某个 .rule 引用 | 删 .xml library 文件 / 改 .drl 加 `import` 段 |
| `DrlParseException: V5.44.3 顶层 import 仅支持 library 路径(双引号字符串)` | import 段写成了 Java class 形式(grammar 仅支持 `import "libs/x.drl";` 形式) | 把 `import com.foo.Bar;` 改成 `import "libs/bar.drl";` 形式 |
| `DrlParseException: Unknown DRL type 'X'. V5.44.3:DRL 顶层 import 段已 declare 路径 [libs/x.drl] 但 type 'X' 不在 builtin` | 顶层 import 路径正确,但 V5.45+ library 实际加载没实现 | V5.44.3 阶段正常行为;V5.45+ 升级后会自动 fetch |
| console-app `/loadXml` 返 400 "loadXml failed: class not found" | `.xml library` 文件被请求加载但 deserializer 已删 | 同上:删 .xml library / 改 .drl |

## 已知限制(V5.45+ 跟进)

- **library 自动加载**:V5.44.3 grammar 接受 import 段,但 library 文件实际
  fetch + type 抽取留 V5.45+ 单独 PR。
- **library 迁移工具脚本**:V5.44.3 仅提供手工转换模板,不强求脚本工具
  (`.xml library → DRL declare 段` 自动 migration tool);V5.45+ 单独 PR
- **declare 段 schema 完整化**:V5.44.3 grammar 已支持 `declare` 段基础形式;
  字段 type alias / 嵌套 declare / annotation 完整化 V5.45+ 跟进

## 引用

- V5.44.3 plan:`/home/fredgu/.claude/plans/luminous-kindling-mist.md`
- V5.42 plan(DRL 4 grammar 起点):`/home/fredgu/git_home/ruleforge/server/CHANGELOG.md`(路线 B 段)
- DRL 4 grammar 入口:`server/lib/ruleforge-core/src/main/antlr4/com/ruleforge/drl/DrlLexer.g4:38`(`DRL_IMPORT`)
- DatatypeResolver.imports API:`server/lib/ruleforge-core/src/main/java/com/ruleforge/ir/drl/DatatypeResolver.java:80`
- BDD 锁:`server/lib/ruleforge-core/src/test/java/com/ruleforge/ir/drl/DrlImportGrammarTest.java`
