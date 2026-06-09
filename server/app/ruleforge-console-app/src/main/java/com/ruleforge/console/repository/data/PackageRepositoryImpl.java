package com.ruleforge.console.repository.data;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruleforge.console.entity.PackageEntity;
import com.ruleforge.console.entity.PackageVersionMappingEntity;
import com.ruleforge.console.mapper.PackageMapper;
import com.ruleforge.console.mapper.PackageVersionMappingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PackageRepositoryImpl implements PackageRepository {

    private final PackageMapper packageMapper;
    private final PackageVersionMappingMapper packageVersionMappingMapper;

    @Override
    public PackageEntity findByProjectIdAndPackageId(Long projectId, String packageId) {
        return packageMapper.selectOne(new LambdaQueryWrapper<PackageEntity>()
                .eq(PackageEntity::getProjectId, projectId)
                .eq(PackageEntity::getPackageId, packageId));
    }

    @Override
    public PackageEntity insert(PackageEntity entity) {
        packageMapper.insert(entity);
        return entity;
    }

    @Override
    public PackageVersionMappingEntity insertMapping(PackageVersionMappingEntity entity) {
        packageVersionMappingMapper.insert(entity);
        return entity;
    }
}
