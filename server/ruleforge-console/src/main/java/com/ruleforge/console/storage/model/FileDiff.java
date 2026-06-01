package com.ruleforge.console.storage.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single file's diff between two Git revisions.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileDiff {

    public enum DiffType {
        ADDED,
        MODIFIED,
        DELETED
    }

    /**
     * File path relative to repo root (e.g., "projectA/folder/file.xml").
     */
    private String filePath;

    /**
     * Whether the file was added, modified, or deleted.
     */
    private DiffType diffType;

    /**
     * Unified diff patch text for this file.
     */
    private String patch;
}
