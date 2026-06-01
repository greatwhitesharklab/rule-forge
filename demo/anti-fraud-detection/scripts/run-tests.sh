#!/bin/bash
# 反欺诈交易检测测试脚本
# 使用方式: ./run-tests.sh [CONSOLE_URL]

set -e

CONSOLE_URL="${1:-http://localhost:8180}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEMO_DIR="$(dirname "$SCRIPT_DIR")"

echo "=============================================="
echo "RuleForge Demo 测试 — 反欺诈交易检测"
echo "=============================================="

PASS=0
FAIL=0
TOTAL=0

# 逐个执行测试用例
python3 -c "
import json
with open('${DEMO_DIR}/test-data/test-cases.json') as f:
    data = json.load(f)
for tc in data['testCases']:
    print(f\"TC:{tc['id']}|{tc['name']}|{json.dumps(tc['input'])}|{json.dumps(tc['expectedOutput'])}\")
" | while IFS='|' read -r tc_id tc_name tc_input tc_expected; do
    echo ""
    echo "测试: ${tc_id} ${tc_name}"

    RESPONSE=$(curl -sf -X POST "${CONSOLE_URL}/api/loan/evaluate" \
      -H "Content-Type: application/json" \
      -d "${tc_input}" 2>&1) || {
        echo "  ✗ API 调用失败"
        continue
    }

    echo "  输入: ${tc_input}"
    echo "  预期: ${tc_expected}"
    echo "  实际: ${RESPONSE}"

    EXPECTED=$(echo "${tc_expected}" | python3 -c "import json,sys; print(json.load(sys.stdin).get('decision',''))" 2>/dev/null)
    ACTUAL=$(echo "${RESPONSE}" | python3 -c "import json,sys; print(json.load(sys.stdin).get('decision',''))" 2>/dev/null)

    if [ "${EXPECTED}" = "${ACTUAL}" ]; then
        echo "  ✓ 通过"
    else
        echo "  ✗ 失败 (预期: ${EXPECTED}, 实际: ${ACTUAL})"
    fi
done

echo ""
echo "=============================================="
echo "测试执行完毕"
echo "=============================================="
