package com.ruleforge.console.batchtest;

import java.util.Map;

/**
 * InputSource 拉取输入时的入参(V5.8.0)
 *
 * 各 InputSource 实现按 type 解释 config:
 *   - FILE:
 *       { "files": "...", "rowCount": 1000 }
 *   - DATASOURCE:
 *       { "datasourceId": 42, "batchInputs": [ {...}, {...}, ... ] }
 *       或
 *       { "datasourceId": 42, "inputField": "id", "valueList": ["x1", "x2", ...] }
 *
 * key 不强制,实现方自己约定,反正 controller 转发前会先 validate。
 */
public record InputSourceConfig(
        String type,
        Map<String, Object> config
) {
}
