package com.ruleforge.v1.ast.library;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * V1 库(vl/cl/pl)。项目级独立 .json 文件,文件树"库"分类;跨 RuleAsset 共享。
 * 序列化 .json,顶层无 type 自识别(文件名/分类驱动)。
 *
 * <p>执行时 V1FlowRunner 把 pl 库的 entries → parameters Map(喂 KnowledgeSession.fireRules),
 * cl → 常量,vl → fact schema 字段(独立库跨流程共享 schema)。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Library {
    private LibraryType type;
    private String name;
    private List<LibraryEntry> entries;

    public LibraryType getType() {
        return type;
    }

    public void setType(LibraryType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<LibraryEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<LibraryEntry> entries) {
        this.entries = entries;
    }
}
