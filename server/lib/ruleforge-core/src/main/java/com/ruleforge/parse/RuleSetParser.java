package com.ruleforge.parse;

import com.ruleforge.builder.RulesRebuilder;
import com.ruleforge.model.rule.Library;
import com.ruleforge.model.rule.LibraryType;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.RuleSet;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import org.dom4j.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Jacky.gao
 * 2014年12月23日
 */
@Setter
public class RuleSetParser implements Parser<RuleSet> {
    private RuleParser ruleParser;
    private LoopRuleParser loopRuleParser;
    private RulesRebuilder rulesRebuilder;

    @Override
    public RuleSet parse(Element element) {
        return parse(element, false);
    }

    public RuleSet parse(Element element, boolean isContainSnapshot) {
        RuleSet ruleSet = new RuleSet();
        String parameterLibrary = element.attributeValue("parameter-library");
        if (StringUtils.isNotEmpty(parameterLibrary)) {
            ruleSet.addLibrary(new Library(parameterLibrary, null, LibraryType.Parameter));
        }
        List<Rule> rules = new ArrayList<>();
        for (Object obj : element.elements()) {
            if (obj == null) {
                continue;
            }
            if (!(obj instanceof Element)) {
                continue;
            }
            Element ele = (Element) obj;
            String name = ele.getName();
            if (ruleParser.support(name)) {
                rules.add(ruleParser.parse(ele));
            } else if (loopRuleParser.support(name)) {
                rules.add(loopRuleParser.parse(ele));
            } else if (name.equals("import-variable-library")) {
                ruleSet.addLibrary(new Library(ele.attributeValue("path"), null, LibraryType.Variable));
            } else if (name.equals("import-constant-library")) {
                ruleSet.addLibrary(new Library(ele.attributeValue("path"), null, LibraryType.Constant));
            } else if (name.equals("import-action-library")) {
                ruleSet.addLibrary(new Library(ele.attributeValue("path"), null, LibraryType.Action));
            } else if (name.equals("import-parameter-library")) {
                ruleSet.addLibrary(new Library(ele.attributeValue("path"), null, LibraryType.Parameter));
            } else if (name.equals("remark")) {
                ruleSet.setRemark(ele.getText());
            }
        }
        ruleSet.setRules(rules);
        rulesRebuilder.rebuildRules(ruleSet.getLibraries(), rules, isContainSnapshot);
        return ruleSet;
    }

    public boolean support(String name) {
        return name.equals("rule-set");
    }

}
