package com.ruleforge.console.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Excel 导入结果
 */
@Data
@AllArgsConstructor
public class TestDataImportResult {
    private Long sessionId;
    private int totalRows;
    private List<TestDataImportErrorMsgDto> errors;
    private boolean maxRowsExceeded;
}
