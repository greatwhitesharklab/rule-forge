package com.ruleforge.console.model;

import lombok.Data;

/**
 * @author Fred Gu
 * @since 2025-04-14 15:04
 */
@Data
public class RuntimeConfig {

    private String packageVersion;
    private String packageId;
    private String timestamp;
    private String env;
}
