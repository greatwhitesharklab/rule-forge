---
title: 决策 API
---

# 决策 API

业务系统通过决策 API 调用规则引擎执行决策。

## 贷款评估

### 评估贷款申请

```
POST /api/loan/evaluate
Content-Type: application/json

{
  "applicantId": "APP001",
  "name": "张三",
  "age": 32,
  "monthlyIncome": 120000,
  "businessYears": 6,
  "creditHistory": "good",
  "debtRatio": 0.15,
  "loanAmount": 2000000,
  "loanTerm": 36
}
```

Response:
```json
{
  "applicantId": "APP001",
  "creditScore": 80,
  "result": "APPROVE",
  "productCode": "P002",
  "productName": "精英贷",
  "maxAmount": 3000000,
  "rate": 0.042,
  "reason": "信用评分良好"
}
```
