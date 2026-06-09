package com.ruleforge.model.rule;

import lombok.Data;

/**
 * @author Jacky.gao
 * 2014年12月30日
 */
@Data
public class Library {
    private String path;
    private String version;
    private LibraryType type;

    public Library() {
    }

    public Library(String path, String version, LibraryType type) {
        this.path = path;
        this.version = version;
        this.type = type;
    }

}
