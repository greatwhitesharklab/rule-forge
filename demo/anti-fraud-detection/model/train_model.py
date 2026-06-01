"""
RuleForge 反欺诈模型训练脚本
训练一个基于 GradientBoostingClassifier 的欺诈交易检测模型
输出: fraud_detection_model.pkl
"""

import numpy as np
import pandas as pd
from sklearn.ensemble import GradientBoostingClassifier
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler
from sklearn.pipeline import Pipeline
from sklearn.metrics import classification_report, roc_auc_score
import joblib
import os

# 生成合成训练数据（生产环境请替换为真实数据）
def generate_synthetic_data(n_samples=10000, random_state=42):
    """生成反欺诈合成数据集"""
    np.random.seed(random_state)

    # 正常交易（90%）
    n_normal = int(n_samples * 0.9)
    # 欺诈交易（10%）
    n_fraud = n_samples - n_normal

    data = []

    # 正常交易特征
    for _ in range(n_normal):
        data.append({
            'transaction_amount': np.random.lognormal(7, 1.5),  # 大部分金额较小
            'frequency_1h': np.random.randint(1, 5),             # 低频
            'avg_amount_7d': np.random.lognormal(7, 1),          # 与当前金额相近
            'merchant_risk_score': np.random.beta(2, 8),         # 低风险商户
            'ip_country_match': 1,                                # IP 匹配
            'device_age_days': np.random.randint(30, 1000),      # 老设备
            'hour_of_day': np.random.choice(range(8, 22)),       # 工作时间
            'distance_from_home': np.random.exponential(10),     # 近距离
            'is_new_device': 0,                                    # 非新设备
            'is_new_merchant': np.random.choice([0, 1], p=[0.8, 0.2]),
            'is_fraud': 0
        })

    # 欺诈交易特征（有明显区别）
    for _ in range(n_fraud):
        data.append({
            'transaction_amount': np.random.lognormal(9, 2),     # 大额
            'frequency_1h': np.random.randint(5, 30),            # 高频
            'avg_amount_7d': np.random.lognormal(6, 1),          # 与当前金额差距大
            'merchant_risk_score': np.random.beta(5, 3),         # 高风险商户
            'ip_country_match': np.random.choice([0, 1], p=[0.7, 0.3]),  # IP 不匹配
            'device_age_days': np.random.randint(0, 30),         # 新设备
            'hour_of_day': np.random.choice([0, 1, 2, 3, 4, 5, 23]),  # 非正常时间
            'distance_from_home': np.random.exponential(500),    # 远距离
            'is_new_device': 1,                                    # 新设备
            'is_new_merchant': np.random.choice([0, 1], p=[0.3, 0.7]),
            'is_fraud': 1
        })

    return pd.DataFrame(data)


def main():
    print("=" * 50)
    print("RuleForge 反欺诈模型训练")
    print("=" * 50)

    # 生成数据
    print("\n[1/4] 生成合成训练数据...")
    df = generate_synthetic_data(n_samples=10000)
    print(f"  数据量: {len(df)} 条 (正常: {(df.is_fraud==0).sum()}, 欺诈: {(df.is_fraud==1).sum()})")

    # 准备特征
    feature_cols = [
        'transaction_amount', 'frequency_1h', 'avg_amount_7d',
        'merchant_risk_score', 'ip_country_match', 'device_age_days',
        'hour_of_day', 'distance_from_home', 'is_new_device', 'is_new_merchant'
    ]
    X = df[feature_cols]
    y = df['is_fraud']

    # 分割数据
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42, stratify=y)
    print(f"  训练集: {len(X_train)}, 测试集: {len(X_test)}")

    # 构建模型管道
    print("\n[2/4] 训练模型...")
    model = Pipeline([
        ('scaler', StandardScaler()),
        ('classifier', GradientBoostingClassifier(
            n_estimators=100,
            max_depth=5,
            learning_rate=0.1,
            random_state=42
        ))
    ])

    model.fit(X_train, y_train)

    # 评估
    print("\n[3/4] 模型评估...")
    y_pred = model.predict(X_test)
    y_prob = model.predict_proba(X_test)[:, 1]

    print("\n" + classification_report(y_test, y_pred, target_names=['正常', '欺诈']))
    auc = roc_auc_score(y_test, y_prob)
    print(f"  AUC-ROC: {auc:.4f}")

    # 保存模型
    print("\n[4/4] 保存模型...")
    output_dir = os.path.dirname(os.path.abspath(__file__))
    output_path = os.path.join(output_dir, 'fraud_detection_model.pkl')
    joblib.dump(model, output_path)

    # 验证模型可加载
    loaded = joblib.load(output_path)
    test_sample = X_test.iloc[:1]
    prob = loaded.predict_proba(test_sample)[0, 1]
    print(f"  模型验证: 预测概率 = {prob:.4f}")
    print(f"  保存路径: {output_path}")
    print(f"  文件大小: {os.path.getsize(output_path) / 1024:.1f} KB")

    print("\n" + "=" * 50)
    print("✓ 模型训练完成!")
    print("使用 setup-model-service.sh 上传到 Model Service")
    print("=" * 50)


if __name__ == '__main__':
    main()
