-- V3.10.0: Deprecate gr_file_version.file_content
-- When Git is the single source, file_content is stored in Git, not DB.
-- The column is kept for rollback safety but marked as deprecated.
ALTER TABLE gr_file_version MODIFY COLUMN file_content LONGTEXT NULL
    COMMENT 'DEPRECATED: file content is now stored in Git. Kept for rollback only.';
