package com.ruleforge.console.repository.data;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruleforge.console.entity.GitDualwriteFailureEntity;
import com.ruleforge.console.mapper.GitDualwriteFailureMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public class GitDualwriteFailureRepositoryImpl implements GitDualwriteFailureRepository {

    private final GitDualwriteFailureMapper mapper;

    @Autowired
    public GitDualwriteFailureRepositoryImpl(GitDualwriteFailureMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public GitDualwriteFailureEntity insert(GitDualwriteFailureEntity entity) {
        mapper.insert(entity);
        return entity;
    }

    @Override
    public long countAll() {
        return mapper.selectCount(null);
    }

    @Override
    public long countSince(Date since) {
        return mapper.selectCount(new LambdaQueryWrapper<GitDualwriteFailureEntity>()
                .ge(GitDualwriteFailureEntity::getOccurredAt, since));
    }

    @Override
    public List<GitDualwriteFailureEntity> findRecent(int limit) {
        return mapper.selectList(new LambdaQueryWrapper<GitDualwriteFailureEntity>()
                .orderByDesc(GitDualwriteFailureEntity::getOccurredAt)
                .last("limit " + limit));
    }

    @Override
    public int deleteOlderThan(Date before) {
        return mapper.delete(new LambdaQueryWrapper<GitDualwriteFailureEntity>()
                .lt(GitDualwriteFailureEntity::getOccurredAt, before));
    }
}
