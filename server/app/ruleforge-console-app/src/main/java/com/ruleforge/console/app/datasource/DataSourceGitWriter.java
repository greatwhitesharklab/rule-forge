package com.ruleforge.console.app.datasource;

import com.ruleforge.console.storage.GitStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * V5.23 — Writes compiled data source .class bytes into the project git repo.
 *
 * <p>All compiled data sources for a project are committed to the same branch
 * (currently {@code main} for simplicity; could be per-DS branch in a future version).
 * The executor-app reads them on startup by listing {@code data_sources/} paths.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSourceGitWriter {

    private static final String BRANCH = "main";

    private final GitStorageService gitStorage;

    /**
     * Write compiled .class bytes to git.
     *
     * @param project  project name (multi-tenant key)
     * @param gitPath  path relative to repo root, e.g. "data_sources/ds_abc/Foo.class"
     * @param bytes    compiled bytecode
     */
    public void writeCompiledClass(String project, String gitPath, byte[] bytes) {
        log.info("Writing compiled data source to git: project={}, path={}, {} bytes",
            project, gitPath, bytes.length);
        gitStorage.writeBytes(project, BRANCH, gitPath, bytes);
    }
}
