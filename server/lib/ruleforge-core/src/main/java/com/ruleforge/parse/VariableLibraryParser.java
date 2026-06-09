package com.ruleforge.parse;

import com.ruleforge.model.library.variable.VariableCategory;
import org.dom4j.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Jacky.gao
 * @since 2014年12月23日
 */
public class VariableLibraryParser implements Parser<List<VariableCategory>> {
    private VariableCategoryParser variableCategoryParser;

    public List<VariableCategory> parse(Element element) {
        List<VariableCategory> variableCategories = new ArrayList<>();
        for (Object obj : element.elements()) {
            if (obj == null || !(obj instanceof Element)) {
                continue;
            }
            Element ele = (Element) obj;
            String name = ele.getName();
            if (variableCategoryParser.support(name)) {
                variableCategories.add(variableCategoryParser.parse(ele));
            }
        }
        return variableCategories;
    }

    public boolean support(String name) {
        return name.equals("variable-library");
    }

    public void setVariableCategoryParser(VariableCategoryParser variableCategoryParser) {
        this.variableCategoryParser = variableCategoryParser;
    }
}
