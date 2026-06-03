package com.ruleforge.console.storage;

/**
 * Thread-local branch context for Git operations.
 * When set, file writes go to the specified user branch instead of main.
 * <p>
 * Usage:
 * <pre>
 *   BranchContext.setBranch("user/zhangsan");
 *   try {
 *       repositoryService.saveFile(...); // writes to user/zhangsan branch
 *   } finally {
 *       BranchContext.clear();
 *   }
 * </pre>
 */
public class BranchContext {

    private static final ThreadLocal<String> CURRENT_BRANCH = new ThreadLocal<>();

    /**
     * Set the current Git branch for this thread.
     */
    public static void setBranch(String branch) {
        CURRENT_BRANCH.set(branch);
    }

    /**
     * Get the current Git branch for this thread.
     * Returns null if no branch is set (defaults to "main").
     */
    public static String getBranch() {
        return CURRENT_BRANCH.get();
    }

    /**
     * Clear the current branch context.
     */
    public static void clear() {
        CURRENT_BRANCH.remove();
    }

    /**
     * Build a user branch name from a username.
     */
    public static String forUser(String username) {
        return "user/" + username;
    }
}
