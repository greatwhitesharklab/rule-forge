package com.ruleforge.runtime.response;


/**
 * @author Jacky.gao
 * @since 2015年1月27日
 */
public interface ExecutionResponse {

    /**
     * @return 返回以毫秒为单位的执行时间
     */
    long getDuration();

    String getVersion();
}
