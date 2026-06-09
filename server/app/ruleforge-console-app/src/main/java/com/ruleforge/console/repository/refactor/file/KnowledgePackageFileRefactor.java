package com.ruleforge.console.repository.refactor.file;

public class KnowledgePackageFileRefactor extends FileRefactor {
    public KnowledgePackageFileRefactor() {
    }

    @Override
    public String doRefactor(String oldPath, String newPath, String content) {
        oldPath = this.perfectPath(oldPath);
        newPath = this.perfectPath(newPath);
        return content.replaceAll(oldPath, newPath);
    }

    @Override
    public boolean support(String path) {
        return path.endsWith("___res__package__file__");
    }
}
