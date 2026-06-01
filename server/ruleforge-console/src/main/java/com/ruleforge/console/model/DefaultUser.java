package com.ruleforge.console.model;

import lombok.Data;

/**
 * @author Jacky.gao
 * @date 2016年5月25日
 */
@Data
public class DefaultUser implements User {
    private String username;
    private String companyId;
    private boolean isAdmin;
    private boolean canImport = false;
    private boolean canExport = false;

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getCompanyId() {
        return companyId;
    }

    @Override
    public boolean isAdmin() {
        return isAdmin;
    }

    @Override
    public boolean isImport() {
        return this.canImport;
    }

    @Override
    public boolean isExport() {
        return this.canExport;
    }
}
