package com.ruleforge.console.repository.refactor.file;

import com.ruleforge.console.repository.model.FileType;

public class DecisionTreeFileRefactor extends FileRefactor {
    public DecisionTreeFileRefactor() {
    }

    @Override
    public String doRefactor(String oldPath, String newPath, String content) {
        oldPath = this.perfectPath(oldPath);
        newPath = this.perfectPath(newPath);
        return content.replaceAll(oldPath, newPath);
    }

    @Override
    public boolean support(String path) {
        return path.toLowerCase().endsWith(FileType.DecisionTree.toString());
    }
}
