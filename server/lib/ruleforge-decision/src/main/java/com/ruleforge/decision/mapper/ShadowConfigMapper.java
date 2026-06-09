package com.ruleforge.decision.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruleforge.decision.entity.ShadowConfig;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 决策陪跑配置表 Mapper
 */
public interface ShadowConfigMapper extends BaseMapper<ShadowConfig> {

    /**
     * 根据主规则包路径查询启用的陪跑配置
     */
    @Select("SELECT * FROM nd_decision_shadow_config WHERE main_rule_package_path = #{mainRulePackagePath} AND enabled = 1")
    List<ShadowConfig> findEnabledByMainPath(@Param("mainRulePackagePath") String mainRulePackagePath);
}
