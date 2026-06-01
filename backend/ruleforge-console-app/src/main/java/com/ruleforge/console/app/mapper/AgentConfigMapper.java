package com.ruleforge.console.app.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruleforge.console.app.agent.model.AgentConfigEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentConfigMapper extends BaseMapper<AgentConfigEntity> {
}
