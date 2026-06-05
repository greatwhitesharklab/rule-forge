package com.ruleforge.console.repository.data;

import com.ruleforge.console.entity.LockEntity;

/**
 * Data access repository for lock entities.
 */
public interface LockRepository {

    LockEntity findByResource(String lockResource);

    LockEntity insert(LockEntity entity);

    boolean deleteById(Long id);
}
