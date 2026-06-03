package com.ruleforge.console.service;

public interface PermissionService {

    boolean isAdmin();

    boolean projectHasPermission(String path);

    boolean projectPackageHasReadPermission(String path);

    boolean projectPackageHasWritePermission(String path);

    boolean fileHasWritePermission(String path);

    boolean fileHasReadPermission(String path);
}
