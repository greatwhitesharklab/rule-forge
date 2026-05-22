---
name: ruleforge
description: Operate RuleForge rule engine service - create projects, write rules, test, and deploy via REST API
---

# RuleForge Rule Engine Skill

Use this skill to interact with a running RuleForge service via its REST API. You can create projects, author rules, test them, and manage deployments.

## Prerequisites

- RuleForge console-app running (default `http://localhost:8081`)
- RuleForge executor-app running (default `http://localhost:8082`)
- The base URL prefix is configured via `${ruleforgeV2.root.path}`, default is `/ruleforgeV2`

Set `RULEFORGE_URL` environment variable if the server is not at localhost:8081. Otherwise assume `http://localhost:8081/ruleforgeV2`.

## API Reference

### Project Management

```
POST /frame/createProject?projectName=xxx&classify=true
GET  /frame/loadProjects
POST /frame/deleteProject?project=xxx
POST /frame/projectExistCheck?projectName=xxx
```

### File Operations

```
POST /frame/createFile?path=/projectName/dir&fileType=Ruleset
POST /frame/createFolder?path=/projectName/dirName
POST /frame/saveFile?path=/projectName/file.rs.xml&newVersion=true&versionComment=xxx
  Body: rule XML content (text/plain)
POST /frame/deleteFile?path=/projectName/file.rs.xml
GET  /frame/loadXml?file=/projectName/file.rs.xml&version=
POST /frame/fileExistCheck?fullFileName=/projectName/file.rs.xml
POST /frame/fileRename?path=xxx&newPath=xxx
```

### Rule Testing

```
POST /test/doTest
  Body: {project, packageId, files, data: [{name, clazz, variables: [{name, label, type, defaultValue}]}]}

POST /test/fast
  Body: similar to doTest but simplified

GET  /test/load/test?project=xxx
```

### Knowledge Package

```
GET  /packageeditor/loadPackages?project=xxx
POST /packageeditor/saveResourcePackages
POST /packageeditor/loadPackageConfig?project=xxx
POST /packageeditor/refreshKnowledgeCache?packageId=xxx
```

### Script Validation

```
POST /common/scriptValidation?content=xxx&type=Script
  type: Script | DecisionNode | ScriptNode
```

### Reference & Import

```
POST /common/loadReferenceFiles?project=xxx&path=xxx
POST /frame/exportProjectBackupFile?project=xxx
POST /frame/importProject  (multipart file)
```

## Rule File Types

| Type | fileType Param | Root XML Tag | Description |
|------|---------------|-------------|-------------|
| Rule Set | `Ruleset` | `<rule-set>` | Wizard-style rules |
| Script Rule Set | `UL` | `rule "name" ... end` | DSL script rules |
| Decision Table | `DecisionTable` | `<decision-table>` | Tabular conditions |
| Script Decision Table | `ScriptDecisionTable` | `<script-decision-table>` | Script-based table |
| Decision Tree | `DecisionTree` | `<decision-tree>` | Tree structure |
| Score Card | `Scorecard` | `<scorecard>` | Scoring model |
| Complex Score Card | `ComplexScorecard` | `<complex-scorecard>` | Advanced scoring |
| Rule Flow | `RuleFlow` | `<rule-flow>` | Flow orchestration |
| Variable Library | `VariableLibrary` | `<variable-library>` | Variable definitions |
| Parameter Library | `ParameterLibrary` | `<parameter-library>` | Parameter definitions |
| Constant Library | `ConstantLibrary` | `<constant-library>` | Constants |
| Action Library | `ActionLibrary` | `<action-library>` | Custom actions |

## Rule XML Templates

### Script Rule (UL)
```
rule "rule01"
if
then
end
```

### Rule Set
```xml
<?xml version="1.0" encoding="utf-8"?>
<rule-set>
</rule-set>
```

### Decision Table
```xml
<?xml version="1.0" encoding="utf-8"?>
<decision-table>
  <cell row="0" col="2" rowspan="1"></cell>
  <cell row="0" col="1" rowspan="1"><joint type="and"/></cell>
  <cell row="0" col="0" rowspan="1"><joint type="and"/></cell>
  <cell row="1" col="2" rowspan="1"></cell>
  <cell row="1" col="1" rowspan="1"><joint type="and"/></cell>
  <cell row="1" col="0" rowspan="1"><joint type="and"/></cell>
  <row num="0" height="40"/>
  <row num="1" height="40"/>
  <col num="0" width="120" type="Criteria"/>
  <col num="1" width="120" type="Criteria"/>
  <col num="2" width="200" type="Assignment"/>
</decision-table>
```

### Decision Tree
```xml
<?xml version="1.0" encoding="utf-8"?>
<decision-tree>
  <variable-tree-node></variable-tree-node>
</decision-tree>
```

### Score Card
```xml
<?xml version="1.0" encoding="utf-8"?>
<scorecard scoring-type="sum" assign-target-type="none">
</scorecard>
```

## Typical Agent Workflow

1. **Create project**: `POST /frame/createProject?projectName=myProject&classify=true`
2. **Check libraries exist**: `GET /frame/loadXml?file=/myProject/___varlib.xml`
3. **Create variable library if needed**: `POST /frame/createFile?path=/myProject&fileType=VariableLibrary`
4. **Create rule file**: `POST /frame/createFile?path=/myProject&fileType=Ruleset`
5. **Author rule content**: `POST /frame/saveFile?path=/myProject/rules.rs.xml` with rule XML body
6. **Validate script**: `POST /common/scriptValidation` if using UL scripts
7. **Test rule**: `POST /test/doTest` with test data
8. **Configure knowledge package**: `POST /packageeditor/loadPackageConfig`
9. **Deploy**: `POST /packageeditor/refreshKnowledgeCache`

## Notes

- All file paths start with `/projectName/`
- The `saveFile` endpoint accepts raw XML/text body
- Variable types: `String`, `Integer`, `Double`, `Long`, `Float`, `BigDecimal`, `Boolean`, `Date`, `List`, `Set`, `Map`, `Enum`, `Object`
- For complex rule authoring, read existing rule files first via `loadXml` to understand the schema patterns
