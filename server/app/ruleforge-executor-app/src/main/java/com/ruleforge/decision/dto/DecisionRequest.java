package com.ruleforge.decision.dto;

import lombok.Data;

import java.util.Map;

/**
 * 贷款决策评估请求
 *
 * <p>Hybrid 数据注入:
 * <ul>
 *   <li><b>eager 字段</b> — {@link #applicant} / {@link #order},业务系统调用时已知
 *       的事实数据(订单金额、产品、申请人姓名等),会立即塞进对应实体,
 *       后续规则读同名字段不再走 DataSourceProvider</li>
 *   <li><b>lazy 字段</b> — 请求体没传的字段,规则读时由
 *       {@code LazyGeneralEntity.get()} 走 {@code DataSourceProvider.fetchFieldValue()}
 *       实时拉(配 {@code Datasource} 表)</li>
 * </ul>
 * 规则 DSL 写 {@code applicant.age} / {@code order.amount} 即可,
 * 字段从哪来由 hybrid 机制自动决定。
 */
@Data
public class DecisionRequest {

    /**
     * 用户ID，必需
     */
    private String userId;

    /**
     * 订单号/申请单号，可选
     */
    private String orderNo;

    /**
     * 规则包路径，必需
     * 例如: "loan/approval"
     */
    private String rulePackagePath;

    /**
     * 决策流ID，必需
     * 例如: "loan-approval-flow"
     */
    private String flowId;

    /**
     * 贷款区域，可选
     * 决定 risk-datasource 数据查询路由：APEX 走本地数据库，ORBIT 走远程 loan-provider
     * 不传时默认走 APEX，行为不变
     */
    private String loanZone;

    /**
     * ORBIT 编码，可选
     * loanZone 为 ORBIT 时使用，标识具体的 orbit 路由
     */
    private String orbitCode;

    // ============== Hybrid facts 注入(V5.18 续) ==============

    /**
     * 申请人 facts — 业务系统在调用时已聚合好的数据
     * (从 CRM / 用户画像 / 申请材料等源头拉来,塞到这一个 map)。
     *
     * <p>字段会被 {@code DecisionServiceImpl.insertEntities()} 当
     * {@code initialValues} 注入 {@code LazyGeneralEntity}(class =
     * {@code com.ruleforge.decision.model.ApplicantModel})。
     * 注入后这些字段被标记为 {@code loadedProperties},
     * 规则 DSL 写 {@code applicant.age} 时直接返回,不走 DataSourceProvider。
     *
     * <p>不传或空 map → 不 eager 注入,规则读 {@code applicant.*}
     * 字段时全部 lazy 查。
     */
    private Map<String, Object> applicant;

    /**
     * 订单 facts — 本次申请的订单上下文(金额、产品、申请时间等)
     * 注入到 {@code OrderModel} 实体。
     *
     * <p>不传或空 map → 不 eager 注入,OrderModel 字段全部 lazy 查。
     */
    private Map<String, Object> order;
}
