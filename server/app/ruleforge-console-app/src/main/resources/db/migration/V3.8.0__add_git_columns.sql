-- V3.8.0: Add Git integration columns for version control migration
-- These columns support the transition from DB-only file storage to Git-based versioning.

-- Track whether a project's Git repository has been initialized
ALTER TABLE gr_project
    ADD COLUMN git_initialized TINYINT(1) DEFAULT 0 COMMENT 'Whether Git repo has been initialized for this project';

-- Store the Git commit SHA for each file version (replaces file_content in later phases)
ALTER TABLE gr_file_version
    ADD COLUMN git_commit_sha VARCHAR(64) NULL COMMENT 'Git commit SHA pointing to file content in Git repo' AFTER file_content;

-- Store Git metadata on project versions
ALTER TABLE gr_project_version
    ADD COLUMN git_commit_sha VARCHAR(64) NULL COMMENT 'Git commit SHA for this package version' AFTER comment,
    ADD COLUMN git_branch VARCHAR(128) NULL COMMENT 'Source branch name (e.g., user/zhangsan)' AFTER git_commit_sha;
