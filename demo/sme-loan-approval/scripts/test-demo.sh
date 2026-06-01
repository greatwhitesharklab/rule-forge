#!/bin/bash
# 小微信贷审批 Demo 测试脚本
# 使用方式: ./test-demo.sh [CONSOLE_URL]

set -e

CONSOLE_URL="${1:-http://localhost:8180}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEMO_DIR="$(dirname "$SCRIPT_DIR")"

echo "=============================================="
echo "RuleForge Demo 测试 — 小微信贷审批"
echo "=============================================="

PASS=0
FAIL=0

# 读取测试用例并逐个执行
TEST_CASES=$(cat ${DEMO_DIR}/test-data/test-cases.json)

# 使用 python 来解析 JSON（更可靠）
python3 -c "
import json, sys
with open('${DEMO_DIR}/test-data/test-cases.json') as f:
    data = json.load(f)
for tc in data['testCases']:
    print(f\"TC:{tc['id']}|{tc['name']}|{json.dumps(tc['input'])}|{json.dumps(tc['expectedOutput'])}\")
" | while IFS='|' read -r tc_id tc_name tc_input tc_expected; do
    echo ""
    echo "测试: ${tc_id} ${tc_name}"

    # 调用决策 API
    RESPONSE=$(curl -sf -X POST "${CONSOLE_URL}/api/loan/evaluate" \
      -H "Content-Type: application/json" \
      -d "${tc_input}" 2>&1) || {
        echo "  ✗ API 调用失败"
        continue
    }

    echo "  输入: ${tc_input}"
    echo "  预期: ${tc_expected}"
    echo "  实际: ${RESPONSE}"

    # 简单校验结果字段
    EXPECTED_RESULT=$(echo "${tc_expected}" | python3 -c "import json,sys; print(json.load(sys.stdin).get('result',''))" 2>/dev/null)
    ACTUAL_RESULT=$(echo "${RESPONSE}" | python3 -c "import json,sys; print(json.load(sys.stdin).get('result',''))" 2>/dev/null)

    if [ "${EXPECTED_RESULT}" = "${ACTUAL_RESULT}" ]; then
        echo "  ✓ 通过"
    else
        echo "  ✗ 失败 (预期: ${EXPECTED_RESULT}, 实际: ${ACTUAL_RESULT})"
    fi
done

echo ""
echo "=============================================="
echo "测试执行完毕"
echo "=============================================="
