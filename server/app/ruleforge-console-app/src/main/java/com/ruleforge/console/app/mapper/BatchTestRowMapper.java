package com.ruleforge.console.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruleforge.console.app.entity.BatchTestRowEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 批量测试数据行 Mapper
 */
public interface BatchTestRowMapper extends BaseMapper<BatchTestRowEntity> {

    @Select("SELECT * FROM rfa_batch_test_row WHERE id = #{id}")
    Map<String, Object> selectMapById(@Param("id") Long id);

    @Select("SELECT * FROM rfa_batch_test_row WHERE session_id = #{sessionId} ORDER BY row_index")
    List<Map<String, Object>> selectBySessionId(@Param("sessionId") Long sessionId);

    @Select("SELECT status, COUNT(*) AS cnt FROM rfa_batch_test_row " +
            "WHERE session_id = #{sessionId} GROUP BY status")
    List<Map<String, Object>> countByStatus(@Param("sessionId") Long sessionId);

    @Update("UPDATE rfa_batch_test_row SET status = #{status}, output_data = #{outputData}, " +
            "error_message = #{errorMessage}, latency_ms = #{latencyMs}, " +
            "http_status = #{httpStatus}, error_code = #{errorCode} WHERE id = #{id}")
    int updateResult(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("outputData") String outputData,
                     @Param("errorMessage") String errorMessage,
                     @Param("latencyMs") Long latencyMs,
                     @Param("httpStatus") Integer httpStatus,
                     @Param("errorCode") String errorCode);
}
