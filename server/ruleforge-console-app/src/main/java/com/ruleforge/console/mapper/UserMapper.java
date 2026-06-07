package com.ruleforge.console.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruleforge.console.app.entity.UserEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户 Mapper — V5.15 权限改造
 */
public interface UserMapper extends BaseMapper<UserEntity> {

    @Select("SELECT * FROM rf_user WHERE username = #{username}")
    UserEntity selectByUsername(@Param("username") String username);
}
