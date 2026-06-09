package com.ruleforge.console.batchtest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ruleforge.console.app.entity.BatchTestRowEntity;

import java.util.List;

/**
 * "输入来源"抽象(V5.8.0 多态化核心)
 *
 * 一个 InputSource 负责"拿出 N 行测试输入"。Spring 容器里所有实现按 type
 * 注册到 BatchTestService 的 map,根据 session.inputSourceType 选取。
 *
 * 当前实现:
 *   - {@link FileInputSource}          从用户上传的 Excel / JSON 拿输入(FLOW+FILE 模式)
 *   - {@link DatasourceInputSource}    调三方数据源取输入(FLOW+DATASOURCE 模式)
 *
 * 跟 {@link BatchTestSubject} 正交:InputSource 只负责"取行",不负责"跑行"。
 * 跑行的活是 Subject。
 */
public interface InputSource {

    /** type 标识:FREE_TEXT(前端内联)/ FILE(Excel)/ DATASOURCE(API 取) */
    String getType();

    /**
     * 取出 N 行输入,落库到 nd_batch_test_row(input_data 列,JSON 序列化)。
     *
     * @param config       入参(FILE 模式含 base64 Excel;DATASOURCE 模式含 datasourceId + batchInputs)
     * @param sessionId    关联到 nd_batch_test_session.id
     * @return 写入的行数(用于更新 session.total_rows)
     */
    int fetchAndInsert(InputSourceConfig config, Long sessionId);

    /**
     * 把行读出来给 Subject.execute() 用 — Subject 拿到的是反序列化后的对象
     * (Flow: ApplicationAllVariableCategoryMap,Datasource: Map<String,Object>)。
     *
     * @param rowEntity   刚 fetch 进来的一行
     * @param typeRef     Jackson TypeReference,告诉 InputSource 反序列化到什么类型
     */
    <T> T deserializeInput(BatchTestRowEntity rowEntity, TypeReference<T> typeRef);
}
