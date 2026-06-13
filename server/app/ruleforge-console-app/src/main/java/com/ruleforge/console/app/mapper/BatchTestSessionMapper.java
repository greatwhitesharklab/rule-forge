package com.ruleforge.console.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruleforge.console.app.entity.BatchTestSessionEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Map;

/**
 * 批量测试会话 Mapper
 */
public interface BatchTestSessionMapper extends BaseMapper<BatchTestSessionEntity> {

    @Select("SELECT * FROM rfa_batch_test_session WHERE id = #{id}")
    Map<String, Object> selectMapById(@Param("id") Long id);

    @Update("UPDATE rfa_batch_test_session SET status = #{status}, progress = #{progress}, " +
            "error_count = #{errorCount}, update_time = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("progress") double progress,
                     @Param("errorCount") int errorCount);
}
