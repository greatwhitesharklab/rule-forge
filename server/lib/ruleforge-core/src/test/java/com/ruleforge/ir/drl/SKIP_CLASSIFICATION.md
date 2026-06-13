# DrlGrammarSmokeTest — 10 @Disabled 分类

V5.49.1 排查结果:10/10 都是 **V5.42.1 grammar 边缘**,归属 V5.42.5 sprint,
**不在 V5.49 范围内**。所有 10 个 `@Disabled` 标注都明确写 "V5.42.5 再补"。

## 分类 (按根因)

### A. LL(*) 决策冲突 (3 个) — grammar rule 顺序问题
需要重新组织 ANTLR4 grammar rule 或拆 sub-rule 消歧。

| Test | 边缘 |
|---|---|
| `lhsFrom` | `pattern from expression` 的 binding prefix 解析冲突 |
| `lhsCollect` | `from collect(...)` 同 from 解析 |
| `stringMethods` | `name[starts-with "Mr"]` pattern 内 stringMethod |

### B. 简化版 grammar (4 个) — V5.42.1 故意没写完整,V5.42.5 补
原 plan 决定先支持 happy path,边缘 case 留给 V5.42.5。

| Test | 边缘 |
|---|---|
| `lhsAccumulateCount` | accumulate 5 内置(count/sum/avg/min/max)init/action/result 3 段 — V5.42.1 砍了 reverse,3 段完整版要补 |
| `lhsAccumulateSum` | 同上,sum 版 |
| `allOperators` | pattern 内 13 种 op(`&&`,`||`,`in`,`not in`,`matches`,`contains`,`memberOf`,`soundslike`...) |
| `rhsStatements` | update / bare function / 完整 methodCall |

### C. 顶层 statement 简化版 (3 个) — query / function / declare
V5.42.1 砍掉,只留 rule + accumulate + extends 这几个核心。

| Test | 边缘 |
|---|---|
| `queryBasic` | `query "Q1"(Integer $min) ... end` parameter type 解析 |
| `functionBasic` | `function Integer myFn(Integer x) { return x + 1; }` returnType 解析 |
| `declareBasic` | `declare Applicant extends Person name : String age : Integer end` UPPER_IDENTIFIER |

## V5.49 行动:不动

这 10 个 skip 是 V5.42 plan **明确划线**的 grammar 边缘,V5.42.5 才是 owner。
V5.49 不删 `@Disabled`,不强行 unskip(grammar 限制是真的,不是测试 bug)。

后续 V5.42.5 sprint 启动时,这份文件就是 backlog 输入。

## V5.49 验证

```bash
mvn -pl server/lib/ruleforge-core test -Dtest=DrlGrammarSmokeTest
# 期望: 22 tests / 0 fail / 10 skip(维持) / 0 error
```
