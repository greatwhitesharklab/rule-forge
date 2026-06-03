package com.ruleforge.console.service.impl;

import com.ruleforge.runtime.cache.CacheUtils;
import com.ruleforge.console.service.RepositoryInterceptor;

public class DefaultRepositoryInterceptor implements RepositoryInterceptor {

    @Override
    public void readFile(String file) {

    }

    @Override
    public void saveFile(String file, String content) {
        removeProjectCache(file);
    }

    @Override
    public void createFile(String file, String content) {
        removeProjectCache(file);
    }

    @Override
    public void deleteFile(String file) {
    }

    @Override
    public void renameFile(String oldFileName, String newFileName) {
        removeProjectCache(oldFileName);
    }

    @Override
    public void createDir(String dir) {

    }

    @Override
    public void createProject(String project) {

    }

    private void removeProjectCache(String file) {
//        if (file.startsWith("/")) {
//            file = file.substring(1);
//        }
//        CacheUtils.getKnowledgeCache().removeKnowledgeByProjectName(file.split("/")[0]);
    }
}
