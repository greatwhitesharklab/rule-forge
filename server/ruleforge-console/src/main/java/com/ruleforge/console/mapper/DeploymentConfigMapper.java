package com.ruleforge.console.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruleforge.console.entity.DeploymentConfigEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeploymentConfigMapper extends MyBaseMapper<DeploymentConfigEntity> {
}
