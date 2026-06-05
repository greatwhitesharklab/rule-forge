package com.ruleforge.console.repository.data;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruleforge.console.entity.LockEntity;
import com.ruleforge.console.mapper.LockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LockRepositoryImpl implements LockRepository {

    private final LockMapper lockMapper;

    @Override
    public LockEntity findByResource(String lockResource) {
        return lockMapper.selectOne(new LambdaQueryWrapper<LockEntity>()
                .eq(LockEntity::getLockResource, lockResource));
    }

    @Override
    public LockEntity insert(LockEntity entity) {
        lockMapper.insert(entity);
        return entity;
    }

    @Override
    public boolean deleteById(Long id) {
        return lockMapper.deleteById(id) > 0;
    }
}
