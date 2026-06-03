package com.ruleforge.console.storage.model;

/**
 * Runtime exception wrapping JGit errors during Git storage operations.
 */
public class GitOperationException extends RuntimeException {

    private final String projectName;

    public GitOperationException(String projectName, String message, Throwable cause) {
        super(String.format("Git operation failed for project [%s]: %s", projectName, message), cause);
        this.projectName = projectName;
    }

    public GitOperationException(String projectName, String message) {
        super(String.format("Git operation failed for project [%s]: %s", projectName, message));
        this.projectName = projectName;
    }

    public String getProjectName() {
        return projectName;
    }
}
