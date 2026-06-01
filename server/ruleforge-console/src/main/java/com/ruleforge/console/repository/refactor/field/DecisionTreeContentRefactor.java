package com.ruleforge.console.repository.refactor.field;

import com.ruleforge.console.repository.model.FileType;
import com.ruleforge.console.repository.refactor.Item;

public class DecisionTreeContentRefactor extends ContentRefactor {
    public DecisionTreeContentRefactor() {
    }

    @Override
    public String doRefactor(String path, String content, Item item) {
        return this.doXmlContentRefactor(path, content, item);
    }

    @Override
    public boolean support(String path) {
        return path.toLowerCase().endsWith(FileType.DecisionTree.toString());
    }
}

