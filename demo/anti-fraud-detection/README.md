# 反欺诈交易检测 Demo

## 场景说明

本演示模拟银行**实时交易风控系统**。每一笔交易经过 AI 模型预测欺诈概率后，由规则引擎结合概率阈值与业务规则做出最终决策：**通过**、**人工审核** 或 **拒绝**。

## AI+规则混合架构

```
交易数据 ──→ ML模型推理(欺诈概率) ──→ 规则引擎决策 ──→ 最终结果
                (PKL模型)             (概率阈值+业务规则)
```

核心思路：

1. **ML 模型**（GradientBoostingClassifier）输出 0~1 之间的欺诈概率
2. **规则引擎**根据概率阈值 + 业务条件（金额、设备、商户等）做出分级决策
3. 两层过滤兼顾模型准确性与业务可解释性

## 涉及的规则类型

| 类型 | 文件 | 说明 |
|------|------|------|
| 向导式规则集 | `rules/fraud-ruleset.json` | 基于欺诈概率+业务条件的分级决策 |
| 决策流 | `rules/fraud-flow.json` | ML预测 → 规则决策 → 分支输出 |
| 变量库 | `variables/variable-library.json` | 交易输入/输出变量定义 |

## 架构说明

```
┌──────────────────────────────────────────────────────────────┐
│                     反欺诈交易检测流程                          │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│   ┌──────────┐    ┌───────────────┐    ┌──────────────────┐  │
│   │ 交易接入  │───→│ ML欺诈概率预测 │───→│   规则引擎决策    │  │
│   │  (Start) │    │ (PKL Model)   │    │ (Fraud RuleSet)  │  │
│   └──────────┘    └───────────────┘    └────────┬─────────┘  │
│                                                 │            │
│                        ┌────────────────────────┼────────┐   │
│                        │                        │        │   │
│                   ┌────▼────┐            ┌──────▼───┐ ┌──▼─────┐
│                   │ 交易通过 │            │ 人工审核  │ │ 交易拒绝│
│                   │ APPROVE │            │ REVIEW   │ │ REJECT │
│                   └─────────┘            └──────────┘ └────────┘
│                                                              │
├──────────────────────────────────────────────────────────────┤
│  规则优先级:                                                  │
│   F001: fraudProbability > 0.8  → REJECT (高风险)            │
│   F002: fraudProbability > 0.5 且 金额 > 10000 → REJECT      │
│   F003: fraudProbability > 0.5 且 金额 <= 10000 → REVIEW    │
│   F004: fraudProbability > 0.3 且 新设备 → REVIEW            │
│   F005: fraudProbability <= 0.3 → APPROVE (低风险)           │
└──────────────────────────────────────────────────────────────┘
```

## 目录结构

```
anti-fraud-detection/
├── README.md                         # 本文件
├── model/
│   └── train_model.py                # 模型训练脚本 (→ fraud_detection_model.pkl)
├── rules/
│   ├── fraud-ruleset.json            # 向导式规则集 (5条规则)
│   └── fraud-flow.json               # 决策流 (ML预测→规则决策→分支)
├── variables/
│   └── variable-library.json         # 变量库 (10个输入 + 4个输出)
├── test-data/
│   └── test-cases.json               # 测试用例 (10条)
└── scripts/
    ├── setup-model-service.sh        # 模型上传到 Model Service
    ├── import-demo.sh                # 导入演示到 RuleForge Console
    └── run-tests.sh                  # 执行测试用例
```

## 如何运行

### 前置条件

- Python 3.8+ (含 scikit-learn, pandas, numpy, joblib)
- RuleForge Console 运行在 `http://localhost:8180`
- RuleForge Executor 运行在 `http://localhost:8280`
- Model Service 运行在 `http://localhost:8501`

### 步骤

```bash
# 1. 训练模型并上传到 Model Service
cd demo/anti-fraud-detection
./scripts/setup-model-service.sh

# 2. 导入演示项目到 Console
./scripts/import-demo.sh

# 3. 运行测试用例
./scripts/run-tests.sh
```

### 仅训练模型

```bash
cd model/
pip install scikit-learn pandas numpy joblib
python3 train_model.py
```

模型输出为 `model/fraud_detection_model.pkl`，可手动上传到 Model Service。

## 模型特征说明

| 特征名 | 说明 | 正常交易 | 欺诈交易 |
|--------|------|---------|---------|
| transaction_amount | 交易金额(元) | 较小 | 大额异常 |
| frequency_1h | 1小时内交易频次 | 1~4次 | 5~30次(高频) |
| avg_amount_7d | 7日平均交易金额 | 与当前相近 | 差距较大 |
| merchant_risk_score | 商户风险评分(0~1) | 低风险 | 高风险 |
| ip_country_match | IP国家是否匹配 | 匹配 | 常不匹配 |
| device_age_days | 设备使用天数 | >30天 | <30天(新设备) |
| hour_of_day | 交易时段(0~23) | 8~21时 | 0~5, 23时 |
| distance_from_home | 离家距离(km) | 近距离 | 远距离 |
| is_new_device | 是否新设备 | 否 | 是 |
| is_new_merchant | 是否新商户 | 常见商户 | 新商户 |
