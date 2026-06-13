package com.ruleforge.dsl;

import com.ruleforge.builder.RulesRebuilder;
import com.ruleforge.dsl.builder.ContextBuilder;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.rule.Library;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.RuleSet;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Collection;
import java.util.List;

/**
 * V5.45.4 — 跟 {@code com.ruleforge.builder.DslRuleSet} 一起删实现,本 class
 * 留作 archive 入口(jar 仍可加载,Spring bean 注册时跑 {@code setApplicationContext}
 * 但 build() / support() 都 0 caller)。
 *
 * <p>V5.44.1 写过 {@code implements DslRuleSet} 用来挂到 ruleforge-core 端
 * 接口上,V5.45.4 删接口后这个 implements 没意义 — drop 掉,免得 JVM 启动
 * 时 verify {@code com.ruleforge.builder.DslRuleSet} 找不到 class 报
 * {@code NoClassDefFoundError}。
 */
public class DSLRuleSetBuilder implements ApplicationContextAware {
    public static final String BEAN_ID = "ruleforge.dslRuleSetBuilder";
    private Collection<ContextBuilder> contextBuilders;
    private RulesRebuilder rulesRebuilder;

    /**
     * V5.45.4 — archive:0 caller 可达;Spring bean 加载时仍跑一遍 ANTLR,但
     * KnowledgeBuilder 不再调此方法。
     */
    public RuleSet build(String script) throws RuleException {
        ANTLRInputStream antlrInputStream = new ANTLRInputStream(script);
        RuleParserLexer lexer = new RuleParserLexer(antlrInputStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        RuleParserParser parser = new RuleParserParser(tokenStream);
        ScriptDecisionTableErrorListener errorListener = new ScriptDecisionTableErrorListener();
        parser.addErrorListener(errorListener);
        BuildRulesVisitor visitor = new BuildRulesVisitor(contextBuilders, tokenStream);
        RuleSet ruleSet = visitor.visitRuleSet(parser.ruleSet());
        rebuildRuleSet(ruleSet);
        String error = errorListener.getErrorMessage();
        if (error != null) {
            throw new RuleException("Script parse error:" + error);
        }
        return ruleSet;
    }

    private void rebuildRuleSet(RuleSet ruleSet) {
        List<Library> libraries = ruleSet.getLibraries();
        List<Rule> rules = ruleSet.getRules();
        rulesRebuilder.rebuildRulesForDSL(libraries, rules);
    }

    public void setRulesRebuilder(RulesRebuilder rulesRebuilder) {
        this.rulesRebuilder = rulesRebuilder;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext)
            throws BeansException {
        contextBuilders = applicationContext.getBeansOfType(ContextBuilder.class).values();
    }
}
