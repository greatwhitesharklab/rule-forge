package com.ruleforge.console.repository.refactor.field;

import com.ruleforge.console.repository.model.FileType;
import com.ruleforge.console.repository.refactor.Item;

public class ScriptRulesetContentRefactor extends ContentRefactor {
    public ScriptRulesetContentRefactor() {
    }

    @Override
    public String doRefactor(String path, String content, Item item) {
        return this.doScriptContentRefactor(path, content, item);
    }

    @Override
    public boolean support(String path) {
        return path.toLowerCase().endsWith(FileType.UL.toString());
    }
}

