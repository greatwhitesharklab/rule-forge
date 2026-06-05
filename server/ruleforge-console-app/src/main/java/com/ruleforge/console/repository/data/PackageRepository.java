package com.ruleforge.console.repository.data;

import com.ruleforge.console.entity.PackageEntity;
import com.ruleforge.console.entity.PackageVersionMappingEntity;

/**
 * Data access repository for package and package version mapping entities.
 */
public interface PackageRepository {

    PackageEntity findByProjectIdAndPackageId(Long projectId, String packageId);

    PackageEntity insert(PackageEntity entity);

    PackageVersionMappingEntity insertMapping(PackageVersionMappingEntity entity);
}
