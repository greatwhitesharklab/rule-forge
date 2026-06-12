package com.ruleforge.builder;

import com.ruleforge.builder.resource.Resource;
import com.ruleforge.builder.resource.ResourceBuilder;
import com.ruleforge.builder.resource.ResourceType;
import com.ruleforge.builder.table.CrosstabRulesBuilder;
import com.ruleforge.builder.table.DecisionTableRulesBuilder;
import com.ruleforge.builder.table.ScriptDecisionTableRulesBuilder;
import com.ruleforge.dsl.DSLRuleSetBuilder;
import com.ruleforge.exception.RuleException;
import com.ruleforge.ir.dmn.DmnResourceDispatcher;
import com.ruleforge.model.crosstab.CrosstabDefinition;
import com.ruleforge.model.decisiontree.DecisionTree;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.rete.Rete;
import com.ruleforge.model.rete.builder.ReteBuilder;
import com.ruleforge.model.rule.Library;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.RuleSet;
import com.ruleforge.model.rule.loop.LoopRule;
import com.ruleforge.model.rule.loop.LoopRuleUnit;
import com.ruleforge.model.scorecard.runtime.ScoreRule;
import com.ruleforge.model.table.DecisionTable;
import com.ruleforge.model.table.ScriptDecisionTable;
import com.ruleforge.runtime.KnowledgePackageWrapper;
import com.ruleforge.runtime.service.KnowledgePackageService;
import lombok.Setter;
import org.dom4j.Element;

import java.io.IOException;
import java.util.*;

@Setter
public class KnowledgeBuilder extends AbstractBuilder {
    private ResourceLibraryBuilder resourceLibraryBuilder;
    private ReteBuilder reteBuilder;
    private RulesRebuilder rulesRebuilder;
    private DecisionTreeRulesBuilder decisionTreeRulesBuilder;
    private DecisionTableRulesBuilder decisionTableRulesBuilder;
    private ScriptDecisionTableRulesBuilder scriptDecisionTableRulesBuilder;
    private DSLRuleSetBuilder dslRuleSetBuilder;
    private CrosstabRulesBuilder crosstabRulesBuilder;
    /**
     * V5.40 — DMN 1.3 资源分流器。资源路径以 {@code .dmn} 结尾时,绕过老 .xml 解析路径,
     * 走 Kie DMN 编译 + 反序列化。{@code .xml} 决策表老路径**完全保留**(破坏性更新是
     * V5.41/V5.42 推 V5.40 这一刀不删 .xml)。
     */
    private DmnResourceDispatcher dmnResourceDispatcher;
    public static final String BEAN_ID = "ruleforge.knowledgeBuilder";

    public KnowledgeBuilder() {
    }

    public KnowledgeBase buildKnowledgeBase(ResourceBase resourceBase) throws RuleException {
        KnowledgePackageService knowledgePackageService = null;
        try {
            knowledgePackageService = (KnowledgePackageService) this.applicationContext.getBean("ruleforgeKnowledgePackageService");
        } catch (Exception ignored) {
        }
        if (knowledgePackageService == null) {
            try {
                knowledgePackageService = (KnowledgePackageService) this.applicationContext.getBean("ruleforge.knowledgePackageService");
            } catch (Exception ignored) {
            }
        }
        List<Rule> rules = new ArrayList<>();
        Map<String, Library> libMap = new HashMap<>();

        for (Resource resource : resourceBase.getResources()) {
            String path = resource.getPath(); // 获取资源路径
            try {
                if (this.dslRuleSetBuilder.support(resource)) {
                    RuleSet ruleSet = this.dslRuleSetBuilder.build(resource.getContent());
                    this.addToLibraryMap(libMap, ruleSet.getLibraries());
                    if (ruleSet.getRules() != null) {
                        // 关联规则与路径
                        this.buildRulesPath(ruleSet.getRules(), path);
                        rules.addAll(ruleSet.getRules());
                    }
                } else if (path != null && path.toLowerCase().endsWith(".dmn")) {
                    // V5.40 — DMN 1.3 决策表路径,绕过老 .xml 解析,直接走 Kie DMN 编译
                    if (this.dmnResourceDispatcher == null) {
                        this.dmnResourceDispatcher = new DmnResourceDispatcher();
                    }
                    DecisionTable table = this.dmnResourceDispatcher.dispatch(path, resource.getContent());
                    this.addToLibraryMap(libMap, table.getLibraries());
                    List tableRules = this.decisionTableRulesBuilder.buildRules(table);
                    this.buildRulesPath(tableRules, path);
                    rules.addAll(tableRules);
                } else {
                    Element root = this.parseResource(resource.getContent());
                    for (ResourceBuilder<?> builder : this.resourceBuilders) {
                        if (builder.support(root)) {
                            Object object = builder.build(root);
                            ResourceType type = builder.getType();
                            List tableRules;
                            if (type.equals(ResourceType.RuleSet)) {
                                RuleSet ruleSet = (RuleSet) object;
                                this.addToLibraryMap(libMap, ruleSet.getLibraries());
                                if (ruleSet.getRules() != null) {
                                    tableRules = ruleSet.getRules();
                                    // 关联规则与路径
                                    this.buildRulesPath(tableRules, path);
                                    this.rulesRebuilder.convertNamedJunctions(tableRules);
                                    rules.addAll(tableRules);
                                }
                            } else {
                                RuleSet ruleSet;
                                if (type.equals(ResourceType.DecisionTree)) {
                                    DecisionTree tree = (DecisionTree) object;
                                    this.addToLibraryMap(libMap, tree.getLibraries());
                                    ruleSet = this.decisionTreeRulesBuilder.buildRules(tree);
                                    this.addToLibraryMap(libMap, ruleSet.getLibraries());
                                    if (ruleSet.getRules() != null) {
                                        // 关联规则与路径
                                        this.buildRulesPath(ruleSet.getRules(), path);
                                        rules.addAll(ruleSet.getRules());
                                    }
                                } else if (type.equals(ResourceType.DecisionTable)) {
                                    DecisionTable table = (DecisionTable) object;
                                    this.addToLibraryMap(libMap, table.getLibraries());
                                    tableRules = this.decisionTableRulesBuilder.buildRules(table);
                                    // 关联规则与路径
                                    this.buildRulesPath(tableRules, path);
                                    rules.addAll(tableRules);
                                } else if (type.equals(ResourceType.CrossDecisionTable)) {
                                    CrosstabDefinition crosstab = (CrosstabDefinition) object;
                                    this.addToLibraryMap(libMap, crosstab.getLibraries());
                                    tableRules = this.crosstabRulesBuilder.buildRules(crosstab);
                                    // 关联规则与路径
                                    this.buildRulesPath(tableRules, path);
                                    rules.addAll(tableRules);
                                } else if (type.equals(ResourceType.ScriptDecisionTable)) {
                                    ScriptDecisionTable table = (ScriptDecisionTable) object;
                                    ruleSet = this.scriptDecisionTableRulesBuilder.buildRules(table);
                                    this.addToLibraryMap(libMap, ruleSet.getLibraries());
                                    if (ruleSet.getRules() != null) {
                                        // 关联规则与路径
                                        this.buildRulesPath(ruleSet.getRules(), path);
                                        rules.addAll(ruleSet.getRules());
                                    }
                                } else if (type.equals(ResourceType.Flow)) {
                                    // Flow resources are now handled by Flowable BPMN engine
                                    // Skip old flow definition processing
                                } else {
                                    // Scorecard 和 ComplexScorecard
                                    ScoreRule rule;
                                    ArrayList listRules;
                                    if (type.equals(ResourceType.Scorecard) || type.equals(ResourceType.ComplexScorecard)) {
                                        rule = (ScoreRule) object;
                                        listRules = new ArrayList<>();
                                        listRules.add(rule);
                                        // 关联规则与路径
                                        this.buildRulesPath(listRules, path);
                                        rules.add(rule);
                                        this.addToLibraryMap(libMap, rule.getLibraries());
                                    }
                                }
                            }
                            break; // 找到合适的 builder 就跳出内部循环
                        }
                    }
                }
            } catch (RuleException e) {
                throw new RuleException(String.format("【%s】【%s】", path, e.getMessage()));
            }
        }

        ResourceLibrary resourceLibrary = this.resourceLibraryBuilder.buildResourceLibrary(libMap.values());
        this.buildLoopRules(rules, resourceLibrary);
        Rete rete = this.reteBuilder.buildRete(rules, resourceLibrary); // 使用收集到的 rules 构建 Rete 网络
        return new KnowledgeBase(rete);
    }

    // 这个方法负责将路径设置到 Rule 对象上
    private void buildRulesPath(List<Rule> rules, String path) {
        for (Rule rule : rules) {
            rule.setFile(path);
        }
    }

    private void buildLoopRules(List<Rule> rules, ResourceLibrary resourceLibrary) {
        for (Rule rule : rules) {
            if (rule instanceof LoopRule) {
                LoopRule loopRule = (LoopRule) rule;
                List<Rule> ruleList = this.buildRules(loopRule);
                Rete rete = this.reteBuilder.buildRete(ruleList, resourceLibrary);
                KnowledgeBase base = new KnowledgeBase(rete);
                KnowledgePackageWrapper knowledgeWrapper = new KnowledgePackageWrapper(base.getKnowledgePackage());
                loopRule.setKnowledgePackageWrapper(knowledgeWrapper);
            }
        }
    }

    private List<Rule> buildRules(LoopRule loopRule) {
        List<Rule> rules = new ArrayList<>();
        List<LoopRuleUnit> units = loopRule.getUnits();

        for (LoopRuleUnit unit : units) {
            Rule rule = new Rule();
            rule.setDebug(loopRule.getDebug());
            rule.setName(loopRule.getName() + "->" + unit.getName());
            rule.setLhs(unit.getLhs());
            rule.setRhs(unit.getRhs());
            rule.setOther(unit.getOther());
            rules.add(rule);
        }

        loopRule.setUnits(null);
        return rules;
    }

    public KnowledgeBase buildKnowledgeBase(RuleSet ruleSet) {
        List<Rule> rules = new ArrayList<>();
        Map<String, Library> libMap = new HashMap<>();
        this.addToLibraryMap(libMap, ruleSet.getLibraries());
        if (ruleSet.getRules() != null) {
            rules.addAll(ruleSet.getRules());
        }

        ResourceLibrary resourceLibrary = this.resourceLibraryBuilder.buildResourceLibrary(libMap.values());
        Rete rete = this.reteBuilder.buildRete(rules, resourceLibrary);
        return new KnowledgeBase(rete);
    }

    private void addToLibraryMap(Map<String, Library> map, List<Library> libraries) {
        if (libraries != null) {
            for (Library lib : libraries) {
                String path = lib.getPath();
                if (!map.containsKey(path)) {
                    map.put(path, lib);
                }
            }
        }
    }

}
