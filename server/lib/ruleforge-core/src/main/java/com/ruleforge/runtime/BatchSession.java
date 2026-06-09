package com.ruleforge.runtime;

/**
 * @author Jacky.gao
 * @since 2015年9月29日
 */
public interface BatchSession {
    public static final int DEFAULT_BATCH_SIZE = 100;
    public static final int DEFAULT_THREAD_SIZE = 10;


    /**
     * 添加一个具体要执行Business对象
     *
     * @param business Business对象实例
     */
    void addBusiness(Business business);


    /**
     * 等待线程池中所有业务线程执行完成，在进行批处理操作时一定要以此方法作为方法调用结尾
     */
    void waitForCompletion();
}
