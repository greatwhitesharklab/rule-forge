package com.ruleforge.console.batchtest;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * BatchTestSubject.execute() 的出参(V5.8.0)
 *
 * 实现方把"一条测试跑出来的结果"包成这个 record 返回,
 * BatchTestService 负责序列化 output + 写到 nd_batch_test_row。
 *
 * 所有字段都可空(成功时 errorCode/errorMessage/httpStatus 看 subject 类型决定填不填)。
 *
 * @param output        跑出来的输出(任意可 JSON 序列化的对象)
 * @param latencyMs     单条执行耗时(必填,所有 subject 都要填)
 * @param httpStatus    HTTP 状态(仅 DATASOURCE 模式用,FLOW 模式为 null)
 * @param errorCode     错误码(FLOW = RuleException.getLabel,DATASOURCE = HTTP code / connector code)
 * @param errorMessage  错误详细描述(成功时为 null)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubjectResult(
        Object output,
        long latencyMs,
        Integer httpStatus,
        String errorCode,
        String errorMessage
) {

    /** 工厂方法:成功结果(无 errorCode/httpStatus) */
    public static SubjectResult success(Object output, long latencyMs) {
        return new SubjectResult(output, latencyMs, null, null, null);
    }

    /** 工厂方法:成功结果(DATASOURCE 用,带 httpStatus) */
    public static SubjectResult successWithStatus(Object output, long latencyMs, int httpStatus) {
        return new SubjectResult(output, latencyMs, httpStatus, null, null);
    }

    /** 工厂方法:失败结果 */
    public static SubjectResult failure(String errorCode, String errorMessage, long latencyMs) {
        return new SubjectResult(null, latencyMs, null, errorCode, errorMessage);
    }

    /** 是否成功(errorCode == null 就算成功) */
    public boolean isSuccess() {
        return errorCode == null;
    }
}
