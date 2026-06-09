package com.ruleforge.console.repository.model;

import java.util.ArrayList;
import java.util.List;

public class RepositoryFile {

    private String name;
    private String fullPath;
    private Type type;
    private LibType libType;
    private List<RepositoryFile> children;
    private Type folderType;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFullPath() {
        return fullPath;
    }

    public void setFullPath(String fullPath) {
        this.fullPath = fullPath;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public LibType getLibType() {
        return libType;
    }

    public void setLibType(LibType libType) {
        this.libType = libType;
    }

    public List<RepositoryFile> getChildren() {
        return children;
    }

    public void setChildren(List<RepositoryFile> children) {
        this.children = children;
    }

    public void addChild(RepositoryFile child, boolean append) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        this.children.add(child);
    }

    public Type getFolderType() {
        return folderType;
    }

    public void setFolderType(Type folderType) {
        this.folderType = folderType;
    }
}
