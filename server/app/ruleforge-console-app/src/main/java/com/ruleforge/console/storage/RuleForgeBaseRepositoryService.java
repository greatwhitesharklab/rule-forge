package com.ruleforge.console.storage;

import com.ruleforge.console.repository.RepositoryReader;
import com.ruleforge.console.repository.model.RepositoryFile;
import com.ruleforge.console.repository.model.ResourcePackage;
import com.ruleforge.console.repository.model.VersionFile;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

public class RuleForgeBaseRepositoryService implements RepositoryReader {

    public static final String RES_PACKAGE_FILE = "___res__package__file__";
    public static final String PACKAGE_CONFIG_FILE = "___package_config__file__";

    @Override
    public List<RepositoryFile> loadProjects(String companyId) throws Exception {
        return null;
    }

    @Override
    public InputStream readFile(String path) throws Exception {
        return null;
    }

    @Override
    public List<ResourcePackage> loadProjectResourcePackages(String project) throws Exception {
        String[] projectArray = project.split(":");
        String version = null;
        if (projectArray.length > 1) {
            project = projectArray[0];
            version = projectArray[1];
        }

        return null;
    }

    @Override
    public List<ResourcePackage> loadProjectResourcePackages(String project, String env) throws Exception {
        return Collections.emptyList();
    }

    @Override
    public List<VersionFile> getVersionFiles(String path) throws Exception {
        return null;
    }

    @Override
    public InputStream readFile(String path, String version) throws Exception {
        return null;
    }
}
