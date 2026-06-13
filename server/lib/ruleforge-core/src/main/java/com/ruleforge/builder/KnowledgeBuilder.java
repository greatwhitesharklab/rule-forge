package com.ruleforge.builder;

import com.ruleforge.builder.resource.Resource;
import com.ruleforge.builder.resource.ResourceBuilder;
import com.ruleforge.builder.resource.ResourceType;
import com.ruleforge.builder.table.CrosstabRulesBuilder;
import com.ruleforge.builder.table.DecisionTableRulesBuilder;
import com.ruleforge.builder.table.ScriptDecisionTableToDrlConverter;
import com.ruleforge.exception.RuleException;
import com.ruleforge.ir.dmn.DmnResourceDispatcher;
import com.ruleforge.ir.drl.DatatypeResolver;
import com.ruleforge.ir.drl.DrlAstVisitor;
import com.ruleforge.ir.drl.DrlResource;
import com.ruleforge.ir.drl.DrlResourceBuilder;
import com.ruleforge.ir.drl.LibraryLoader;
import com.ruleforge.ir.pmml.PmmlResourceDispatcher;
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
    /**
     * V5.45.4 — DSL chain runtime 真删:KnowledgeBuilder 不再持有
     * {@link com.ruleforge.builder.DslRuleSet} 引用(老接口 V5.45.4 已删)。
     * ruleforge-dsl module 仍存在作 archive(可加载 classpath),但 production
     * runtime 不可达 — KnowledgeBuilder 不会再调 .support() / .build() 老 DSL 链。
     * V5.43 行为兼容:遇到 .ul 老格式静默 0 rule,不抛错。
     */
    private CrosstabRulesBuilder crosstabRulesBuilder;
    /**
     * V5.40 — DMN 1.3 资源分流器。资源路径以 {@code .dmn} 结尾时,绕过老 .xml 解析路径,
     * 走 Kie DMN 编译 + 反序列化。{@code .xml} 决策表老路径**完全保留**(破坏性更新是
     * V5.41/V5.42 推 V5.40 这一刀不删 .xml)。
     */
    private DmnResourceDispatcher dmnResourceDispatcher;
    /**
     * V5.41 — PMML 4.4 资源分流器。资源路径以 {@code .pmml} 结尾时,绕过老 .xml 解析路径,
     * 走 pmml4s 编译 + 反序列化。{@code .xml} 评分卡/树老路径**完全保留**(V5.41 这一刀
     * 跟 V5.40 同款,只加并行新格式,删 .xml 路径是 V5.42/V5.43 后置 PR)。
     */
    private PmmlResourceDispatcher pmmlResourceDispatcher;
    /**
     * V5.45.2 — library 加载 SPI。{@code null} 时,.drl 路径退到 V5.44.4 行为(imports
     * 列表收集但不实际加载);非 null 时,.drl 顶层 import 段每条路径 BFS 递归加载。
     * console-app 注入 LocalLibraryLoader(读 repository .drl),executor-app 注入
     * RemoteLibraryLoader(调 console /fileSource 端点),ruleforge-core 单元测试
     * 注入 mock。
     */
    private LibraryLoader libraryLoader;
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
                // V5.45.4 — DSL chain runtime 真删:KnowledgeBuilder 不再调
                // dslRuleSetBuilder.support() / .build() — 老 .ul 资源走 0 rule
                // 静默跳过(跟 V5.43 行为一致),不抛错。ruleforge-dsl module 仍可
                // 加载作 archive,但 production runtime 不可达。
                if (path != null && path.toLowerCase().endsWith(".dmn")) {
                    // V5.40 — DMN 1.3 决策表路径,绕过老 .xml 解析,直接走 Kie DMN 编译
                    if (this.dmnResourceDispatcher == null) {
                        this.dmnResourceDispatcher = new DmnResourceDispatcher();
                    }
                    DecisionTable table = this.dmnResourceDispatcher.dispatch(path, resource.getContent());
                    this.addToLibraryMap(libMap, table.getLibraries());
                    List tableRules = this.decisionTableRulesBuilder.buildRules(table);
                    this.buildRulesPath(tableRules, path);
                    rules.addAll(tableRules);
                } else if (path != null && path.toLowerCase().endsWith(".pmml")) {
                    // V5.41 — PMML 4.4 评分卡/树路径,绕过老 .xml 解析,走 pmml4s 编译
                    if (this.pmmlResourceDispatcher == null) {
                        this.pmmlResourceDispatcher = new PmmlResourceDispatcher();
                    }
                    // V5.41.3 顶层字段阶段:pmmlModel 顶层已填,子结构(cells/rows/<Node> 树)
                    // 留空 — 暂不展开,等 V5.41.4.1 单独 PR 完整展开(超出本 PR scope)。
                    // 当前走 .pmml 路径产 0 rule(空表/空树语义),不抛异常。
                    this.pmmlResourceDispatcher.dispatch(path, resource.getContent());
                } else if (path != null && (path.toLowerCase().endsWith(".drl")
                        || path.toLowerCase().endsWith(".drlrd")
                        || path.toLowerCase().endsWith(".dslr"))) {
                    // V5.45.2 — DRL 4 grammar 资源路径,两阶段 parse:
                    //   阶段 1:lenient 模式 parse,只抽顶层 import 段
                    //   阶段 2:BFS 调 libraryLoader 拉每个 library declare types,
                    //          register 进 DatatypeResolver(builtin 优先,不覆盖)
                    //   阶段 3:用已注册 resolver 跑 DrlResourceBuilder
                    // V5.44.4 老路径(无 libraryLoader)行为兼容:imports 列表收集但不加载
                    DatatypeResolver resolver = new DatatypeResolver();
                    if (this.libraryLoader != null) {
                        org.antlr.v4.runtime.CharStream input = org.antlr.v4.runtime.CharStreams.fromString(resource.getContent());
                        com.ruleforge.drl.DrlLexer lexer = new com.ruleforge.drl.DrlLexer(input);
                        org.antlr.v4.runtime.CommonTokenStream tokens = new org.antlr.v4.runtime.CommonTokenStream(lexer);
                        com.ruleforge.drl.DrlParser parser = new com.ruleforge.drl.DrlParser(tokens);
                        DrlAstVisitor phase1 = new DrlAstVisitor(resolver, true);
                        phase1.visit(parser.compilationUnit());

                        java.util.Deque<String> queue = new java.util.ArrayDeque<>(phase1.getImports());
                        java.util.Set<String> visited = new java.util.LinkedHashSet<>();
                        while (!queue.isEmpty()) {
                            String libPath = queue.poll();
                            if (!visited.add(libPath)) continue;
                            java.util.Map<String, DatatypeResolver.TypeInfo> libTypes =
                                this.libraryLoader.loadLibrary(libPath, path);
                            for (java.util.Map.Entry<String, DatatypeResolver.TypeInfo> e : libTypes.entrySet()) {
                                if (!resolver.isKnown(e.getKey())) {
                                    resolver.register(e.getKey(), e.getValue());
                                }
                            }
                        }
                    }
                    List<Rule> drlRules = new DrlResourceBuilder(resolver)
                        .build(new DrlResource(resource.getContent(), path));
                    if (drlRules != null && !drlRules.isEmpty()) {
                        this.buildRulesPath(drlRules, path);
                        rules.addAll(drlRules);
                    }
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
                                    // V5.44.2 — 行为补回:ScriptDecisionTable → DRL 4 字符串
                                    // → 走 V5.42 既有 DrlResourceBuilder → List<Rule>。
                                    // 转换器逻辑见 ScriptDecisionTableToDrlConverter。
                                    ScriptDecisionTable scriptTable = (ScriptDecisionTable) object;
                                    String drl = new ScriptDecisionTableToDrlConverter().convert(scriptTable);
                                    List<Rule> drlRules = new DrlResourceBuilder(new DatatypeResolver())
                                        .build(new DrlResource(drl, path));
                                    if (drlRules != null && !drlRules.isEmpty()) {
                                        this.buildRulesPath(drlRules, path);
                                        rules.addAll(drlRules);
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
