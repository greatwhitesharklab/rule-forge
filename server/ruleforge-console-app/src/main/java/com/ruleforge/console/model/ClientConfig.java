package com.ruleforge.console.model;

import lombok.Data;

/**
 * @author Jacky.gao
 * @since 2016年8月11日
 */
@Data
public class ClientConfig {
    private String name;
    private String client;
    private String project;
    private ClientEnv env;

    public enum ClientEnv {
        TEST, PRODUCT
    }
}
