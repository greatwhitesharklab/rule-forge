package com.ruleforge.runtime.event;

/**
 * @author Jacky.gao
 * @since 2015年7月20日
 */
public interface AgendaEventListener extends KnowledgeEventListener {
    /**
     * 当规则条件满足时，创建一个包装规则的Activation对象时触发
     *
     * @param event ActivationCreatedEvent对象
     */
    void activationCreated(ActivationCreatedEvent event);

    /**
     * 当因某个Fact对象从WorkingMemory中删除导致某个规则条件不满足时触发
     *
     * @param event ActivationCancelledEvent对象
     */
    void activationCancelled(ActivationCancelledEvent event);

    /**
     * 在执行某个满足条件的规则动作之前触发
     *
     * @param event ActivationBeforeFiredEvent对象
     */
    void beforeActivationFired(ActivationBeforeFiredEvent event);

    /**
     * 在执行某个满足条件的规则动作之前触发
     *
     * @param event ActivationAfterFiredEvent对象
     */
    void afterActivationFired(ActivationAfterFiredEvent event);
}
