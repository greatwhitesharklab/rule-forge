package com.ruleforge.console.mapper;

import com.ruleforge.console.entity.ProjectVersionMappingEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectVersionMappingMapper extends MyBaseMapper<ProjectVersionMappingEntity> {
}