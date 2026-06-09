package com.ruleforge.console.servlet.common;

import java.util.List;

public class RefFile {

    private String path;
    private String name;
    private String type;
    private String editor;
    private String version;
    private List<String> versionHistory;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getEditor() {
        return editor;
    }

    public void setEditor(String editor) {
        this.editor = editor;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<String> getVersionHistory() {
        return versionHistory;
    }

    public void setVersionHistory(List<String> versionHistory) {
        this.versionHistory = versionHistory;
    }
}
