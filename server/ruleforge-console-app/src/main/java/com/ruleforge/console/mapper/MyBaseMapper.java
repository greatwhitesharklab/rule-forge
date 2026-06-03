package com.ruleforge.console.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface MyBaseMapper<T> extends BaseMapper<T> {

    /**
     * 批量插入
     *
     * @param batchList 插入数据
     * @return 插入数量
     */
    int insertBatchSomeColumn(@Param("list") List<T> batchList);

}
