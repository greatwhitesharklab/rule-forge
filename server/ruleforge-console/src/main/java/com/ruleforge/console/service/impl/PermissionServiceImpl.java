package com.ruleforge.console.service.impl;

import com.ruleforge.console.service.PermissionService;
import org.springframework.stereotype.Service;

@Service
public class PermissionServiceImpl implements PermissionService {
    @Override
    public boolean isAdmin() {
        return true;
    }

    @Override
    public boolean projectHasPermission(String path) {
        return true;
    }

    @Override
    public boolean projectPackageHasReadPermission(String path) {
        return true;
    }

    @Override
    public boolean projectPackageHasWritePermission(String path) {
        return true;
    }

    @Override
    public boolean fileHasWritePermission(String path) {
        return true;
    }

    @Override
    public boolean fileHasReadPermission(String path) {
        return true;
    }
}
