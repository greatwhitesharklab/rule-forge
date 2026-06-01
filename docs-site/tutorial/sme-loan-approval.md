---
title: 小微信贷审批教程
---

# 小微信贷审批教程

本教程将带你构建一个完整的小微信贷审批决策系统，展示评分卡、规则集、决策表和决策流的组合使用。

## 场景介绍

模拟银行小微企业贷款申请的自动化审批流程：

```
申请信息 → 信用评分卡 → 风险规则集 → 产品匹配 → 额度计算 → 审批结果
```

## 前置条件

确保 RuleForge 已启动：

```bash
docker compose up -d
```

## Step 1: 准备 Demo 数据

```bash
cd demo/sme-loan-approval
```

Demo 包含以下规则文件：

| 文件 | 规则类型 | 说明 |
|------|---------|------|
| `credit-scorecard.json` | 评分卡 | 5 维度信用评分（满分 100） |
| `risk-ruleset.json` | 规则集 | 5 条风险规则 |
| `product-table.json` | 决策表 | 评分→产品匹配 |
| `approval-flow.json` | 决策流 | 编排完整审批流程 |

## Step 2: 导入规则

```bash
./scripts/import-demo.sh http://localhost:8180
```

脚本会自动创建项目、导入所有规则文件并发布知识包。

## Step 3: 查看规则

打开 http://localhost，在项目列表中找到「小微信贷审批」。

### 评分卡维度

| 维度 | 满分 | 评分标准 |
|------|------|---------|
| 年龄 | 25 | 25-35岁最高分 |
| 月收入 | 35 | 15万以上最高分 |
| 经营年限 | 30 | 5年以上最高分 |
| 信用记录 | 30 | "良好"最高分 |
| 负债率 | 25 | <20%最高分 |

### 风险规则

| 规则 | 条件 | 结果 |
|------|------|------|
| 高负债拒绝 | 负债率 > 70% | REJECT |
| 低信用拒绝 | 评分 < 40 | REJECT |
| 中等审核 | 评分 40-60 | MANUAL_REVIEW |
| 优质通过 | 评分 ≥ 60 | APPROVE |

## Step 4: 执行测试

```bash
./scripts/test-demo.sh http://localhost:8180
```

10 个测试用例将覆盖所有审批路径：
- 优质客户大额贷款 → APPROVE
- 高负债率 → REJECT
- 中等信用评分 → MANUAL_REVIEW

## Step 5: API 调用

直接通过 API 测试单个申请：

```bash
curl -X POST http://localhost:8180/api/loan/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "applicantId": "TEST001",
    "name": "测试客户",
    "age": 30,
    "monthlyIncome": 100000,
    "businessYears": 5,
    "creditHistory": "good",
    "debtRatio": 0.15,
    "loanAmount": 1500000,
    "loanTerm": 36
  }'
```

预期结果：

```json
{
  "creditScore": 80,
  "result": "APPROVE",
  "productCode": "P002",
  "productName": "精英贷"
}
```

## Step 6: 在 UI 中编辑规则

1. 打开评分卡编辑器，调整评分维度和分值
2. 修改风险规则的条件阈值
3. 使用「快速测试」验证修改效果
4. 满意后发布新版本

## Step 7: 查看 AI 分析

如果启用了 Agent 功能，可以在「分析」页面查看：
- 规则覆盖率（哪些规则被触发、哪些从未命中）
- 决策趋势（通过/拒绝/审核的比例变化）
- 异常检测（异常决策偏差告警）

## 总结

本教程展示了 RuleForge 在信贷审批场景中的完整应用：
- **评分卡**量化客户信用
- **规则集**实现风险控制
- **决策表**匹配贷款产品
- **决策流**编排完整流程

下一步：[反欺诈交易检测教程](/tutorial/anti-fraud-detection) — 体验 AI+规则混合决策
