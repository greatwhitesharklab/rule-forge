#!/bin/bash
# 反欺诈模型上传到 Model Service
# 使用方式: ./setup-model-service.sh [MODEL_SERVICE_URL]

set -e

MODEL_URL="${1:-http://localhost:8501}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEMO_DIR="$(dirname "$SCRIPT_DIR")"

echo "=============================================="
echo "RuleForge Demo — 反欺诈模型服务配置"
echo "Model Service URL: ${MODEL_URL}"
echo "=============================================="

# 训练模型（如果 PKL 文件不存在）
if [ ! -f "${DEMO_DIR}/model/fraud_detection_model.pkl" ]; then
    echo "[1/3] 训练欺诈检测模型..."
    cd "${DEMO_DIR}/model"
    python3 train_model.py
    echo "✓ 模型训练完成"
else
    echo "[1/3] 使用已有模型文件"
fi

# 等待 Model Service 就绪
echo "[2/3] 等待 Model Service 启动..."
for i in $(seq 1 30); do
    if curl -sf "${MODEL_URL}/health" > /dev/null 2>&1; then
        echo "✓ Model Service 已就绪"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "✗ Model Service 启动超时"
        echo "请先启动: docker compose up model-service"
        exit 1
    fi
    sleep 2
done

# 上传模型
echo "[3/3] 上传模型到 Model Service..."
UPLOAD_RESPONSE=$(curl -sf -X POST "${MODEL_URL}/models" \
  -F "file=@${DEMO_DIR}/model/fraud_detection_model.pkl" \
  -F "model_id=fraud_detection_v1" \
  -F "name=Fraud Detection Model v1") && echo "✓ 模型上传成功" || {
    echo "✗ 模型上传失败"
    exit 1
}

# 激活模型
echo "激活模型..."
curl -sf -X POST "${MODEL_URL}/models/fraud_detection_v1/activate" > /dev/null && echo "✓ 模型已激活" || {
    echo "✗ 模型激活失败"
    exit 1
}

# 验证模型
echo "验证模型..."
MODEL_INFO=$(curl -sf "${MODEL_URL}/models/fraud_detection_v1")
echo "  模型信息: ${MODEL_INFO}"

echo ""
echo "=============================================="
echo "✓ 反欺诈模型服务配置完成！"
echo "模型 ID: fraud_detection_v1"
echo "=============================================="
