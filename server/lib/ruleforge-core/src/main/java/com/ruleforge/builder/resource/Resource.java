package com.ruleforge.builder.resource;


import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author Jacky.gao
 * 2014年12月22日
 */
@Getter
@AllArgsConstructor
public class Resource {
    private final String content;
    private final String path;
    private final String projectVersion;
}
