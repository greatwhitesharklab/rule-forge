package com.ruleforge.console.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.ruleforge.console.entity.FileEntity;
import com.ruleforge.console.entity.FileRelationEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface FileMapper extends MyBaseMapper<FileEntity> {

    @Select("select f.* from gr_file_relation fr left join gr_file f on fr.descendant = f.id ${ew.customSqlSegment}")
    List<FileEntity> selectListByAncestor(@Param(Constants.WRAPPER) Wrapper<FileRelationEntity> queryWrapper);
}
