package com.ruleforge.console.app.draft;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

/**
 * 草稿测试用例 mapper (V5.22.1)
 *
 * <p>权限域:ruleforge_db (由 ruleforgeDataSource 注入)
 */
@Mapper
public interface TestCaseMapper extends BaseMapper<TestCaseEntity> {

    @Select("""
            SELECT * FROM rf_draft_test_case
            WHERE draft_id = #{draftId}
            ORDER BY created_at ASC
            """)
    List<TestCaseEntity> listByDraftId(@Param("draftId") String draftId);

    @Select("""
            SELECT * FROM rf_draft_test_case
            WHERE test_case_id = #{testCaseId}
            """)
    TestCaseEntity selectByTestCaseId(@Param("testCaseId") String testCaseId);

    @Delete("""
            DELETE FROM rf_draft_test_case
            WHERE test_case_id = #{testCaseId}
            """)
    int deleteByTestCaseId(@Param("testCaseId") String testCaseId);

    @Delete("""
            DELETE FROM rf_draft_test_case
            WHERE draft_id = #{draftId}
            """)
    int deleteByDraftId(@Param("draftId") String draftId);
}
