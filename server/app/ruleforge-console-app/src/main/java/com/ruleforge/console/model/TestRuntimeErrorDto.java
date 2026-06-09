package com.ruleforge.console.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author Fred
 * @since 2025/6/17 16:34
 */
@Data
@AllArgsConstructor
public class TestRuntimeErrorDto {

    private Integer dataNum;
    private String msg;
}
