package com.ruleforge.console.repository;

import com.ruleforge.console.repository.model.RepositoryFile;
import com.ruleforge.console.repository.model.ResourcePackage;
import com.ruleforge.console.repository.model.VersionFile;

import java.io.InputStream;
import java.util.List;

public interface RepositoryReader {

    List<RepositoryFile> loadProjects(String companyId) throws Exception;

    InputStream readFile(String path) throws Exception;

    List<ResourcePackage> loadProjectResourcePackages(String project) throws Exception;

    List<ResourcePackage> loadProjectResourcePackages(String project, String env) throws Exception;

    List<VersionFile> getVersionFiles(String path) throws Exception;

    InputStream readFile(String path, String version) throws Exception;
}
