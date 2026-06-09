-- V3.8.1: Package version mapping table for Git-based file tracking
CREATE TABLE IF NOT EXISTS gr_package_version_mapping (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    package_version_id BIGINT NOT NULL COMMENT 'References gr_project_version.id',
    file_path VARCHAR(512) NOT NULL COMMENT 'File path relative to project root',
    git_blob_sha VARCHAR(64) NOT NULL COMMENT 'Git blob SHA for the file at this version',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_package_version_id (package_version_id),
    INDEX idx_file_path (file_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Maps package versions to specific file Git blobs';
