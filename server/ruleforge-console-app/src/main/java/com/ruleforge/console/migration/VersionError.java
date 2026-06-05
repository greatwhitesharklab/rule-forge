package com.ruleforge.console.migration;

/**
 * 5.10-B: 单版本错误.
 *
 * gitCommitSha 在 commit 成功但 updateGitCommitSha 失败时填上,标识"孤儿 commit"。
 */
public class VersionError {

    private String filePath;
    private String versionNum;
    private String errorType;
    private String message;
    private String gitCommitSha;

    public VersionError() {
    }

    public VersionError(String filePath, String versionNum, String errorType, String message) {
        this.filePath = filePath;
        this.versionNum = versionNum;
        this.errorType = errorType;
        this.message = message;
    }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getVersionNum() { return versionNum; }
    public void setVersionNum(String versionNum) { this.versionNum = versionNum; }
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getGitCommitSha() { return gitCommitSha; }
    public void setGitCommitSha(String gitCommitSha) { this.gitCommitSha = gitCommitSha; }
}
