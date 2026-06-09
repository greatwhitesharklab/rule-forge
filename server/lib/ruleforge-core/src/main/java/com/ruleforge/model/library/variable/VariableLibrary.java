package com.ruleforge.model.library.variable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Jacky.gao
 * @since 2014年12月23日
 */
public class VariableLibrary {
    private List<VariableCategory> variableCategories;

    public List<VariableCategory> getVariableCategories() {
        return variableCategories;
    }

    public void setVariableCategories(List<VariableCategory> variableCategories) {
        this.variableCategories = variableCategories;
    }

    public void addVariableCategory(VariableCategory category) {
        if (variableCategories == null) {
            variableCategories = new ArrayList<>();
        }
        variableCategories.add(category);
    }
}
