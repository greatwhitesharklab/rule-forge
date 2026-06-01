package com.ruleforge.builder.resource;

/**
 * @author Jacky.gao
 * @author fred
 * 2014年12月22日
 */
public interface ResourceProvider {
    Resource provide(String path, String version, String projectVersion, boolean containSnapshot);

    boolean support(String path);
}
