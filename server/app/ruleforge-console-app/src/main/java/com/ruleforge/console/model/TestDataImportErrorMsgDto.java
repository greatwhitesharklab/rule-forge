package com.ruleforge.console.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author Fred
 * @since 2025/8/24 22:29
 */
@Data
@AllArgsConstructor
public class TestDataImportErrorMsgDto {

    private String sheetName;
    private int sheetRowId;
    private String sheetFieldName;
    private String errorMsg;
}
