package com.ruleforge.console.service;

public interface RepositoryInterceptor {

    void readFile(String file);

    void saveFile(String file, String content);

    void createFile(String file, String content);

    void deleteFile(String file);

    void renameFile(String oldFileName, String newFileName);

    void createDir(String dir);

    void createProject(String project);

}
