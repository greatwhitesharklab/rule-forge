package com.ruleforge.model.library;

import com.ruleforge.exception.RuleException;
import com.ruleforge.model.library.action.ActionLibrary;
import com.ruleforge.model.library.constant.ConstantCategory;
import com.ruleforge.model.library.constant.ConstantLibrary;
import com.ruleforge.model.library.variable.VariableCategory;
import com.ruleforge.model.library.variable.VariableLibrary;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Jacky.gao
 * @since 2015年1月6日
 */
public class ResourceLibrary {
    private List<ConstantCategory> constantCategories;
    private List<ActionLibrary> actionLibraries;
    private List<VariableCategory> variableCategories;

    public ResourceLibrary() {
    }

    public ResourceLibrary(List<VariableLibrary> variableLibraries, List<ActionLibrary> actionLibraries, List<ConstantLibrary> constantLibraries) {
        this.variableCategories = new ArrayList<>();
        this.actionLibraries = new ArrayList<>();
        this.constantCategories = new ArrayList<>();
        for (VariableLibrary vl : variableLibraries) {
            for (VariableCategory category : vl.getVariableCategories()) {
                variableCategories.add(category);
            }
        }
        this.actionLibraries.addAll(actionLibraries);
        for (ConstantLibrary cl : constantLibraries) {
            for (ConstantCategory cc : cl.getCategories()) {
                this.constantCategories.add(cc);
            }
        }
    }

    public VariableCategory getVariableCategory(String categoryName) {
        for (VariableCategory category : variableCategories) {
            if (category.getName().equals(categoryName)) {
                return category;
            }
        }
        throw new RuleException("Variable category [" + categoryName + "] not exist");
    }

    public List<ActionLibrary> getActionLibraries() {
        return actionLibraries;
    }

    public List<VariableCategory> getVariableCategories() {
        return variableCategories;
    }

    public List<ConstantCategory> getConstantCategories() {
        return constantCategories;
    }
}
