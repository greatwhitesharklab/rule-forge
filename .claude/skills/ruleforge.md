---
name: ruleforge
description: Operate RuleForge rule engine - create projects, write rules, test, and deploy via CLI or REST API
---

# RuleForge Rule Engine Skill

Use this skill to interact with RuleForge via the `rf` CLI tool or REST API.

## Quick Start

```bash
# CLI is at cli/ directory, run with uv
cd /home/fredgu/git_home/ruleforge/cli && uv run rf <command>

# Or if installed globally
rf <command>
```

## CLI Commands

```
rf project ls [--name NAME] [--types TYPES]
rf project create NAME
rf project delete PATH

rf file create PATH --type TYPE
rf file get PATH [--version VER]
rf file save PATH --content TEXT | --file LOCAL_FILE
rf file delete PATH
rf file rename PATH --new-path NEW
rf file versions PATH

rf rule search --key KEY --project NAME
rf rule load-xml --files FILES
rf rule resource-tree [--project NAME]

rf test variables --file PATH
rf test fast --file PATH [--data JSON] [--flow-id ID]
rf test load-data --app-id ID --project ID

rf package ls --project NAME
rf package config --project NAME
rf variable generate-fields --class CLASSNAME
```

## REST API (if CLI unavailable)

Base URL: `http://localhost:8081/ruleforgeV2`

| Endpoint | Method | Description |
|---|---|---|
| `/frame/loadProjects` | POST | List projects |
| `/frame/createProject?newProjectName=xxx` | POST | Create project |
| `/frame/createFile?path=xxx&type=xxx` | POST | Create file |
| `/frame/fileSource?path=xxx` | POST | Get file content |
| `/common/saveFile?file=xxx&content=xxx` | POST | Save file |
| `/common/loadXml?files=xxx` | POST | Load parsed XML |
| `/test/variableCategories/load` | POST | Get test variables (body: FastTestDto) |
| `/test/fast` | POST | Run test (body: FastTestDto) |
| `/packageeditor/loadPackages?project=xxx` | POST | List packages |

## File Types

| Type | Param | Extension | Root Element |
|---|---|---|---|
| Rule Set | `Ruleset` | `.rs.xml` | `<rule-set>` |
| Script Rule | `UL` | `.ul` | DSL (not XML) |
| Decision Table | `DecisionTable` | `.dt.xml` | `<decision-table>` |
| Script Decision Table | `ScriptDecisionTable` | `.sdt.xml` | `<script-decision-table>` |
| Decision Tree | `DecisionTree` | `.dt.xml` | `<decision-tree>` |
| Scorecard | `Scorecard` | `.sc` | `<scorecard>` |
| Complex Scorecard | `ComplexScorecard` | `.csc.xml` | `<complex-scorecard>` |
| Crosstab | `Crosstab` | `.ct.xml` | `<crosstab>` |
| Rule Flow | `RuleFlow` | `.rl.xml` | `<rule-flow>` |
| Variable Library | `VariableLibrary` | | `<variable-library>` |
| Constant Library | `ConstantLibrary` | | `<constant-library>` |
| Parameter Library | `ParameterLibrary` | | `<parameter-library>` |
| Action Library | `ActionLibrary` | | `<action-library>` |

## Agent Workflow

1. `rf project create myProject`
2. `rf file create /myProject/___varlib.xml --type VariableLibrary` — create variable library
3. `rf file save /myProject/___varlib.xml --content '<variable-library>...'` — define variables
4. `rf file create /myProject/rule.dt.xml --type DecisionTable` — create rule file
5. `rf file save /myProject/rule.dt.xml --content '<decision-table>...'` — write rule content
6. `rf test variables --file /myProject/rule.dt.xml` — check what inputs are needed
7. `rf test fast --file /myProject/rule.dt.xml --data '[...]'` — run test

## XML Rule Format Reference

### Common Library Imports

All rule types support importing libraries at the top:
```xml
<import-variable-library path="/project/___varlib.xml"/>
<import-constant-library path="/project/___constlib.xml"/>
<import-action-library path="/project/___actionlib.xml"/>
<import-parameter-library path="/project/___paramlib.xml"/>
```

### Common Attributes

Most rule types support these optional attributes on the root element:
- `salience` — priority (integer)
- `effective-date` — start date (yyyy-MM-dd)
- `expires-date` — end date (yyyy-MM-dd)
- `enabled` — enable flag (boolean, default true)
- `debug` — debug flag (boolean, default false)

### Variable Library (`<variable-library>`)

```xml
<?xml version="1.0" encoding="utf-8"?>
<variable-library>
  <category name="user" type="Custom" clazz="user">
    <var name="age" label="年龄" type="Integer" act="InOut"/>
    <var name="income" label="收入" type="Double" act="InOut"/>
    <var name="name" label="姓名" type="String" act="InOut"/>
    <var name="level" label="等级" type="String" act="InOut"/>
  </category>
  <category name="__prj" type="Parameter">
    <var name="param1" label="参数1" type="String" act="InOut"/>
  </category>
</variable-library>
```

**Key points:**
- `name` is the internal identifier, `label` is display name
- `clazz` must match `GeneralEntity.targetClass` (usually simple name like "user", not FQCN)
- `act` values: `InOut`, `In`, `Out`
- Parameter category uses `type="Parameter"` and `name="__prj"`
- Variable types: `String`, `Integer`, `Double`, `Long`, `Float`, `BigDecimal`, `Boolean`, `Date`, `List`, `Set`, `Map`, `Enum`, `Object`

### Decision Table (`<decision-table>`)

```xml
<?xml version="1.0" encoding="utf-8"?>
<decision-table salience="10">
  <import-variable-library path="/project/___varlib.xml"/>

  <!-- Column definitions: type can be Criteria or Assignment -->
  <col num="0" width="120" type="Criteria" var-category="user" var="age" var-label="年龄" datatype="Integer"/>
  <col num="1" width="120" type="Criteria" var-category="user" var="income" var-label="收入" datatype="Double"/>
  <col num="2" width="200" type="Assignment" var-category="user" var="level" var-label="等级" datatype="String"/>

  <!-- Row definitions -->
  <row num="0" height="40"/>
  <row num="1" height="40"/>
  <row num="2" height="40"/>

  <!-- Condition cells use <joint> with <condition> + <value> -->
  <cell row="0" col="0" rowspan="1">
    <joint type="And">
      <condition op="&gt;=">
        <value type="Input" content="25"/>
      </condition>
    </joint>
  </cell>
  <cell row="0" col="1" rowspan="1">
    <joint type="And">
      <condition op="&gt;=">
        <value type="Input" content="5000"/>
      </condition>
    </joint>
  </cell>
  <!-- Assignment cell uses <value> directly -->
  <cell row="0" col="2" rowspan="1">
    <value type="Input" content="VIP"/>
  </cell>

  <!-- More rows... -->
  <cell row="1" col="0" rowspan="1">
    <joint type="And">
      <condition op="&gt;=">
        <value type="Input" content="18"/>
      </condition>
    </joint>
  </cell>
  <cell row="1" col="2" rowspan="1">
    <value type="Input" content="NORMAL"/>
  </cell>
</decision-table>
```

**Column types:** `Criteria` (condition), `Assignment` (set value), `ConsolePrint`, `ExecuteMethod`

**Condition operators:** `=`, `!=`, `>`, `<`, `>=`, `<=`, `Contains`, `StartsWith`, `EndsWith`, `In`, `NotIn`, `Match`, `Null`, `NotNull`

**Value types:**
- `Input` — literal value via `content` attribute
- `Variable` — reference via `var` + `var-category`
- `Constant` — reference via `const` + `const-category`
- `Parameter` — reference via `var`
- `Method` — call bean method via `bean-name` + `method-name`

### Decision Tree (`<decision-tree>`)

```xml
<?xml version="1.0" encoding="utf-8"?>
<decision-tree>
  <import-variable-library path="/project/___varlib.xml"/>

  <variable-tree-node>
    <left type="variable" var="age" var-category="user" datatype="Integer"/>

    <!-- Branch 1: age >= 18 -->
    <condition-tree-node op="GreaterThen">
      <value type="Input" content="18"/>
      <!-- Leaf action -->
      <action-tree-node>
        <var-assign var="level" var-category="user" datatype="String">
          <value type="Input" content="TREE_APPROVED"/>
        </var-assign>
      </action-tree-node>
    </condition-tree-node>

    <!-- Branch 2: else (age < 18) -->
    <condition-tree-node op="LessThen">
      <value type="Input" content="18"/>
      <action-tree-node>
        <var-assign var="level" var-category="user" datatype="String">
          <value type="Input" content="TREE_REJECT"/>
        </var-assign>
      </action-tree-node>
    </condition-tree-node>
  </variable-tree-node>
</decision-tree>
```

**Key points:**
- Root must be `<variable-tree-node>` with a `<left>` defining the variable to branch on
- Branches use `<condition-tree-node>` with `op` attribute — NOT `<condition>`
- Each branch can have nested `<variable-tree-node>` for multi-level trees
- Leaf actions go in `<action-tree-node>` containing action elements

**Operators:** `Equals`, `NotEquals`, `GreaterThen`, `LessThen`, `GreaterThenEquals`, `LessThenEquals`, `In`, `NotIn`, `Null`, `NotNull`, `Contains`, `StartsWith`, `EndsWith`, `Match`

### Scorecard (`<scorecard>`)

```xml
<?xml version="1.0" encoding="utf-8"?>
<scorecard scoring-type="sum" assign-target-type="variable"
           var-category="user" var="score" var-label="评分" datatype="Integer">

  <import-variable-library path="/project/___varlib.xml"/>

  <col num="0" width="120" type="Custom" custom-label="属性"/>
  <col num="1" width="150" type="Condition"/>
  <col num="2" width="100" type="Score"/>
  <row num="0" height="40"/>
  <row num="1" height="40"/>
  <row num="2" height="40"/>

  <attribute-row row-number="0">
    <condition-row row-number="0"/>
    <condition-row row-number="1"/>
  </attribute-row>
  <attribute-row row-number="1">
    <condition-row row-number="2"/>
  </attribute-row>

  <!-- Attribute cell -->
  <card-cell type="attribute" row="0" col="0" var="age" var-label="年龄" category="user" datatype="Integer"/>
  <!-- Condition cell -->
  <card-cell type="condition" row="0" col="1">
    <joint type="And">
      <condition op="&gt;=">
        <value type="Input" content="25"/>
      </condition>
    </joint>
  </card-cell>
  <!-- Score cell -->
  <card-cell type="score" row="0" col="2">
    <value type="Input" content="50"/>
  </card-cell>

  <card-cell type="attribute" row="1" col="0" var="age" var-label="年龄" category="user" datatype="Integer"/>
  <card-cell type="condition" row="1" col="1">
    <joint type="And">
      <condition op="&lt;">
        <value type="Input" content="25"/>
      </condition>
    </joint>
  </card-cell>
  <card-cell type="score" row="1" col="2">
    <value type="Input" content="10"/>
  </card-cell>
</scorecard>
```

**Key points:**
- `scoring-type`: `sum` or `weightsum`
- `assign-target-type`: `variable`, `parameter`, or `none`
- Grid layout with `<col>`, `<row>`, `<card-cell>`, `<attribute-row>`, `<condition-row>`
- Each `<attribute-row>` groups one attribute with its condition rows
- Cell types: `attribute`, `condition`, `score`, `custom`
- Must declare ALL rows and cols that cells reference

### Complex Scorecard (`<complex-scorecard>`)

```xml
<?xml version="1.0" encoding="utf-8"?>
<complex-scorecard scoring-type="sum" assign-target-type="variable"
                   var-category="user" var="score" var-label="评分" datatype="Integer">

  <import-variable-library path="/project/___varlib.xml"/>

  <col num="0" width="120" type="Criteria" var-category="user" var="age" var-label="年龄" datatype="Integer"/>
  <col num="1" width="100" type="Score"/>
  <row num="0" height="40"/>
  <row num="1" height="40"/>
  <row num="2" height="40"/>

  <!-- Criteria cell (same as DecisionTable) -->
  <cell row="0" col="0" rowspan="1">
    <joint type="And">
      <condition op="&gt;=">
        <value type="Input" content="25"/>
      </condition>
    </joint>
  </cell>
  <!-- Score cell -->
  <cell row="0" col="1" rowspan="1">
    <value type="Input" content="50"/>
  </cell>

  <cell row="1" col="0" rowspan="1">
    <joint type="And">
      <condition op="&lt;">
        <value type="Input" content="25"/>
      </condition>
    </joint>
  </cell>
  <cell row="1" col="1" rowspan="1">
    <value type="Input" content="10"/>
  </cell>
</complex-scorecard>
```

**Key points:**
- Uses table layout (col/row/cell) like DecisionTable, not the card-cell grid
- Column types: `Criteria`, `Score`, `Custom`
- Must declare all rows — missing `<row>` declarations cause ArrayIndexOutOfBounds
- Cells use same `<joint>`/`<condition>`/`<value>` pattern as DecisionTable

### Rule Set (`<rule-set>`)

```xml
<?xml version="1.0" encoding="utf-8"?>
<rule-set>
  <import-variable-library path="/project/___varlib.xml"/>

  <rule name="rule1" salience="10">
    <if>
      <lhs>
        <criteria id="c1">
          <left type="variable" var="age" var-category="user" datatype="Integer"/>
          <op op="GreaterThenEquals"/>
          <value type="Input" content="18"/>
        </criteria>
      </lhs>
    </if>
    <then>
      <var-assign var="level" var-category="user" datatype="String">
        <value type="Input" content="APPROVED"/>
      </var-assign>
    </then>
  </rule>
</rule-set>
```

### Script Rule (UL)

Not XML — DSL format:
```
rule "rule01"
if
    user.age >= 18
then
    user.level = "APPROVED"
end
```

### Crosstab (`<crosstab>`)

```xml
<?xml version="1.0" encoding="utf-8"?>
<crosstab assign-target-type="variable"
          var-category="user" var="result" var-label="结果" datatype="String">
  <import-variable-library path="/project/___varlib.xml"/>

  <cross-row number="1" type="top"/>
  <cross-row number="2" type="left"/>
  <cross-column number="1" type="left"/>
  <cross-column number="2" type="top"/>
  <cross-column number="3" type="top"/>

  <condition-cross-cell row="1" col="1" row-span="1" col-span="2">
    <joint type="And">
      <condition op="&gt;=">
        <value type="Input" content="25"/>
      </condition>
    </joint>
  </condition-cross-cell>
  <value-cross-cell row="2" col="2">
    <value type="Input" content="APPROVED"/>
  </value-cross-cell>
</crosstab>
```

## Common Pitfalls

1. **Variable `clazz` mismatch**: VariableLibrary category `clazz` must match `GeneralEntity.targetClass`. Use simple names like `"user"`, not FQCN like `"com.example.User"`.

2. **DecisionTree element names**: Use `<condition-tree-node>`, NOT `<condition>`. Use `<variable-tree-node>`, NOT `<variable>`.

3. **ComplexScorecard rows**: Must declare `<row>` for every row number that cells reference. Missing rows cause runtime errors.

4. **Scorecard grid alignment**: `<attribute-row>` `row-number` must match the `<card-cell>` row numbers. Each attribute-row's condition-rows must cover all condition rows for that attribute.

5. **XML escaping**: Use `&gt;=` for `>=`, `&lt;` for `<`, `&amp;` for `&` in attribute values.

6. **File path convention**: Files stored as `/projectName/filename.ext`. Library files often use prefix `___` (e.g., `___varlib.xml`).

## Test Data Format

`rf test fast --data` accepts JSON matching VariableCategory structure:

```json
[
  {
    "name": "user",
    "clazz": "user",
    "variables": [
      {"name": "age", "label": "年龄", "type": "Integer", "defaultValue": "25"},
      {"name": "income", "label": "收入", "type": "Double", "defaultValue": "5000"}
    ]
  }
]
```

The response returns output variables with their computed values in `defaultValue`.
