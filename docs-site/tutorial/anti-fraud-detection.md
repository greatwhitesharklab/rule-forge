---
title: 反欺诈交易检测教程
---

# 反欺诈交易检测教程

本教程展示 RuleForge 的 **AI+规则混合决策** 能力：ML 模型预测欺诈概率，规则引擎基于概率进行最终决策。

## 场景介绍

模拟银行实时交易风控系统：

```
交易数据 → ML模型(欺诈概率) → 规则引擎(阈值+业务规则) → 决策结果
```

核心思路：**AI 提供智能推理，规则提供确定性护栏**。

## 前置条件

确保 RuleForge 全栈服务已启动（包括 Model Service）：

```bash
docker compose up -d
```

## Step 1: 训练 ML 模型

```bash
cd demo/anti-fraud-detection/model

# 安装依赖
pip install scikit-learn pandas numpy joblib

# 训练模型
python3 train_model.py
```

训练脚本会：
- 生成 10,000 条合成交易数据（90% 正常 / 10% 欺诈）
- 训练 GradientBoostingClassifier（10 个特征）
- 输出 `fraud_detection_model.pkl`

### 模型特征

| 特征 | 说明 | 欺诈模式 |
|------|------|---------|
| transaction_amount | 交易金额 | 异常大额 |
| frequency_1h | 1小时内交易频次 | 高频刷单 |
| avg_amount_7d | 7日平均交易金额 | 与历史偏差大 |
| merchant_risk_score | 商户风险评分 | 高风险商户 |
| ip_country_match | IP国家是否匹配 | 境外IP |
| device_age_days | 设备使用天数 | 新设备 |
| hour_of_day | 交易时段 | 深夜交易 |
| distance_from_home | 离家距离 | 异地消费 |
| is_new_device | 是否新设备 | 新设备 |
| is_new_merchant | 是否新商户 | 首次商户 |

## Step 2: 上传模型到 Model Service

```bash
cd demo/anti-fraud-detection
./scripts/setup-model-service.sh http://localhost:8501
```

脚本会自动：
- 等待 Model Service 就绪
- 上传 PKL 模型
- 激活模型（model_id: `fraud_detection_v1`）
- 验证模型可用

## Step 3: 导入反欺诈规则

```bash
./scripts/import-demo.sh http://localhost:8180
```

### 反欺诈规则设计

| 规则 | 条件 | 决策 | 风险等级 |
|------|------|------|---------|
| 高概率拒绝 | fraudProb > 0.8 | REJECT | HIGH |
| 中概率+大额拒绝 | fraudProb > 0.5 AND amount > 1万 | REJECT | HIGH |
| 中概率+小额审核 | fraudProb > 0.5 AND amount <= 1万 | MANUAL_REVIEW | MEDIUM |
| 低概率+新设备审核 | fraudProb > 0.3 AND isNewDevice | MANUAL_REVIEW | LOW |
| 低风险通过 | fraudProb <= 0.3 | APPROVE | NONE |

关键设计：**规则以 ML 概率为输入，而非原始特征**。这样 ML 模型可以独立迭代优化，规则层保持稳定。

## Step 4: 测试混合决策

```bash
./scripts/run-tests.sh http://localhost:8180
```

10 个测试场景覆盖：
- 正常交易 → APPROVE（低概率）
- 深夜大额 + 新设备 → REJECT（高概率）
- 境外IP + 高频 → REJECT（中概率+大额）
- 新设备中等金额 → MANUAL_REVIEW（低概率+新设备）

## Step 5: API 调用

```bash
# 测试可疑交易
curl -X POST http://localhost:8180/api/loan/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "TXN999",
    "transactionAmount": 50000,
    "frequency1h": 15,
    "avgAmount7d": 800,
    "merchantRiskScore": 0.7,
    "ipCountryMatch": 0,
    "deviceAgeDays": 5,
    "hourOfDay": 3,
    "distanceFromHome": 800,
    "isNewDevice": true,
    "isNewMerchant": true
  }'
```

预期：ML 模型输出高欺诈概率 → 规则引擎判定 REJECT

## Step 6: 模型迭代

1. 用真实数据重新训练模型
2. 上传新版本到 Model Service
3. 使用影子测试对比新旧模型效果
4. 确认无误后切换到新模型

## Step 7: 监控与告警

在生产环境中，可以监控：
- 欺诈概率分布趋势
- 各决策路径的占比变化
- 模型预测准确率（基于人工审核反馈）
- 规则命中率

## AI+规则混合的价值

| 维度 | 纯规则 | 纯 AI | AI+规则（RuleForge） |
|------|--------|-------|---------------------|
| 可审计 | 是 | 否 | 是 |
| 自适应 | 否 | 是 | 是 |
| 合规 | 是 | 否 | 是 |
| 维护 | 复杂 | 黑盒 | 简单 |
| 准确率 | 中 | 高 | 高 |

## 总结

本教程展示了 RuleForge 的 AI+规则混合决策能力：
- **ML 模型**提供智能推理（欺诈概率）
- **规则引擎**提供确定性护栏（阈值决策）
- **可审计**每个决策的完整链路

这是 RuleForge 在金融场景中的核心优势。
