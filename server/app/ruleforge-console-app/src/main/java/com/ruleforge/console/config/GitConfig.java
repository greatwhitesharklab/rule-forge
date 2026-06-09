package com.ruleforge.console.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Git-based file version storage.
 *
 * Properties are bound from "ruleforge.git.*" in application.yml.
 * Git is always active — it is the primary content store.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ruleforge.git")
public class GitConfig {

    /**
     * Base directory for local Git repositories.
     * Each project gets a subdirectory: {base}/{projectName}/
     */
    private String base = "/opt/data";

    /**
     * Remote Git server base URL for push operations.
     * Example: "http://git.example.com/ruleforge/"
     * Full remote URL = {remoteUrl}{projectName}.git
     * If empty, push operations are skipped.
     */
    private String remoteUrl;
}
