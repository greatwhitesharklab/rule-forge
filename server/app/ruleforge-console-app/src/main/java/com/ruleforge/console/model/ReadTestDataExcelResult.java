package com.ruleforge.console.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * @author Fred
 * @since 2025/8/24 23:00
 */
@Data
@AllArgsConstructor
public class ReadTestDataExcelResult {

    private final List<ApplicationAllVariableCategoryMap> applicationAllVariableCategoryMapList;
    private final List<TestDataImportErrorMsgDto> errorMsgDtoList;
}
