package com.ruleforge.console.storage.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of a Git merge operation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MergeResult {

    public enum Status {
        /** Fast-forward merge, no new merge commit needed. */
        FAST_FORWARD,
        /** Clean three-way merge, a merge commit was created. */
        MERGED,
        /** Conflicts detected, merge was aborted. */
        CONFLICTING
    }

    /**
     * The merge outcome status.
     */
    private Status status;

    /**
     * SHA of the resulting commit (merge commit or fast-forward target).
     * Null if status is CONFLICTING.
     */
    private String mergeCommitSha;

    /**
     * List of file paths with conflicts.
     * Empty if status is FAST_FORWARD or MERGED.
     */
    private List<String> conflictingFiles = new ArrayList<>();

    public static MergeResult fastForward(String sha) {
        return new MergeResult(Status.FAST_FORWARD, sha, new ArrayList<>());
    }

    public static MergeResult merged(String sha) {
        return new MergeResult(Status.MERGED, sha, new ArrayList<>());
    }

    public static MergeResult conflicting(List<String> files) {
        return new MergeResult(Status.CONFLICTING, null, files);
    }
}
