package com.ruleforge.console.repository.model;

import java.util.Date;

public class VersionFile {

    private String name;
    private String path;
    private String versionNum;
    private Long versionNumReal;
    private String comment;
    private String beforeComment;
    private String afterComment;
    private String content;
    private String createUser;
    private Date createDate;
    private String auditStatus;
    private long projectId;
    private Long projectVersionNumReal;
    private String testAuditStatus;
    private Type folderType;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getVersionNum() {
        return versionNum;
    }

    public void setVersionNum(String versionNum) {
        this.versionNum = versionNum;
    }

    public Long getVersionNumReal() {
        return versionNumReal;
    }

    public void setVersionNumReal(Long versionNumReal) {
        this.versionNumReal = versionNumReal;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getBeforeComment() {
        return beforeComment;
    }

    public void setBeforeComment(String beforeComment) {
        this.beforeComment = beforeComment;
    }

    public String getAfterComment() {
        return afterComment;
    }

    public void setAfterComment(String afterComment) {
        this.afterComment = afterComment;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(String createUser) {
        this.createUser = createUser;
    }

    public Date getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Date createDate) {
        this.createDate = createDate;
    }

    public String getAuditStatus() {
        return auditStatus;
    }

    public void setAuditStatus(String auditStatus) {
        this.auditStatus = auditStatus;
    }

    public long getProjectId() {
        return projectId;
    }

    public void setProjectId(long projectId) {
        this.projectId = projectId;
    }

    public Long getProjectVersionNumReal() {
        return projectVersionNumReal;
    }

    public void setProjectVersionNumReal(Long projectVersionNumReal) {
        this.projectVersionNumReal = projectVersionNumReal;
    }

    public String getTestAuditStatus() {
        return testAuditStatus;
    }

    public void setTestAuditStatus(String testAuditStatus) {
        this.testAuditStatus = testAuditStatus;
    }

    public Type getFolderType() {
        return folderType;
    }

    public void setFolderType(Type folderType) {
        this.folderType = folderType;
    }
}
