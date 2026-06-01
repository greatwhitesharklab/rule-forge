package com.ruleforge.console.model;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Fred
 * @since 2019-12-27 5:31 PM
 */
@Data
public class PackageConfig {
    private String version;
    private String testVersion;
    private Boolean lock;
    private Map<String, Integer> auditStatusMap;
    private Map<String, String> versionDiffMap;
    private Map<String, RuntimeConfig> runtimeConfigMap;

    public PackageConfig() {
        this.auditStatusMap = new HashMap<>();
        this.versionDiffMap = new HashMap<>();
        this.runtimeConfigMap = new HashMap<>();
    }

}
