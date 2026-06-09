package com.ruleforge.console.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruleforge.console.app.entity.UserProjectPermissionEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户项目权限 Mapper — V5.15 权限改造
 */
public interface UserProjectPermissionMapper extends BaseMapper<UserProjectPermissionEntity> {

    @Select("SELECT * FROM rf_user_project_permission WHERE user_id = #{userId}")
    List<UserProjectPermissionEntity> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM rf_user_project_permission WHERE user_id = #{userId} AND project = #{project}")
    UserProjectPermissionEntity selectByUserIdAndProject(@Param("userId") Long userId,
                                                          @Param("project") String project);

    @Delete("DELETE FROM rf_user_project_permission WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
