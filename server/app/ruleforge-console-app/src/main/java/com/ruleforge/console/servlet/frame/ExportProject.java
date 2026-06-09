package com.ruleforge.console.servlet.frame;

import com.ruleforge.console.repository.model.VersionFile;

import java.util.HashMap;
import java.util.Map;

public class ExportProject {

    private final Map<String, VersionFile> versionFileMap = new HashMap<>();

    public Map<String, VersionFile> getVersionFileMap() {
        return versionFileMap;
    }
}
