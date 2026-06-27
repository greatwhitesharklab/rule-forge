package com.ruleforge.console.app.v1;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * {@code rf_v1_publish} Mapper(V7.6 V1 原生发布)。
 *
 * <p>复用 {@code ruleforgeSqlSessionFactory}(跟 {@code rf_file} / {@code rf_draft} 同源)。
 */
@Mapper
public interface V1PublishMapper extends BaseMapper<V1PublishEntity> {

    @Select("SELECT * FROM rf_v1_publish WHERE project = #{project} AND flow_path = #{flowPath} LIMIT 1")
    V1PublishEntity selectByFlow(@Param("project") String project, @Param("flowPath") String flowPath);
}
