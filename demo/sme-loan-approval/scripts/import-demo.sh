#!/bin/bash
# 小微信贷审批 Demo 导入脚本
# 使用方式: ./import-demo.sh [CONSOLE_URL]

set -e

CONSOLE_URL="${1:-http://localhost:8180}"
API_BASE="${CONSOLE_URL}/ruleforgeV2"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEMO_DIR="$(dirname "$SCRIPT_DIR")"

echo "=============================================="
echo "RuleForge Demo — 小微信贷审批"
echo "Console URL: ${CONSOLE_URL}"
echo "=============================================="

# 等待 Console 就绪
echo "等待 Console 服务启动..."
for i in $(seq 1 30); do
    if curl -sf "${CONSOLE_URL}/actuator/health" > /dev/null 2>&1; then
        echo "✓ Console 服务已就绪"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "✗ Console 服务启动超时"
        exit 1
    fi
    sleep 2
done

# 创建项目
echo "创建演示项目..."
PROJECT_RESPONSE=$(curl -sf -X POST "${API_BASE}/frame/saveProject" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"小微信贷审批\",
    \"description\": \"小微企业贷款自动化审批决策演示\",
    \"type\": \"demo\"
  }") || {
    echo "✗ 创建项目失败（可能项目已存在）"
}
echo "✓ 项目创建完成"

# 导入变量库
echo "导入变量库..."
curl -sf -X POST "${API_BASE}/frame/saveFile" \
  -H "Content-Type: application/json" \
  -d @${DEMO_DIR}/variables/variable-library.json > /dev/null && echo "✓ 变量库导入成功" || echo "⚠ 变量库导入跳过"

# 导入规则文件
for rule_file in ${DEMO_DIR}/rules/*.json; do
    rule_name=$(basename "$rule_file" .json)
    echo "导入规则: ${rule_name}..."
    curl -sf -X POST "${API_BASE}/frame/saveFile" \
      -H "Content-Type: application/json" \
      -d @"${rule_file}" > /dev/null && echo "  ✓ ${rule_name} 导入成功" || echo "  ⚠ ${rule_name} 导入跳过"
done

# 发布知识包
echo "发布知识包..."
curl -sf -X POST "${API_BASE}/frame/publishPackage" \
  -H "Content-Type: application/json" \
  -d "{\"project\": \"小微信贷审批\", \"version\": \"1.0.0\"}" > /dev/null && echo "✓ 知识包发布成功" || echo "⚠ 知识包发布跳过"

echo ""
echo "=============================================="
echo "✓ Demo 导入完成！"
echo "访问 http://localhost 查看规则"
echo "运行 ./test-demo.sh 执行测试"
echo "=============================================="
