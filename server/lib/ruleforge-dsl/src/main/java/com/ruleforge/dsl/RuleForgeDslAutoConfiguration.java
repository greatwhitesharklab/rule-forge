package com.ruleforge.dsl;

import com.ruleforge.builder.RulesRebuilder;
import com.ruleforge.dsl.builder.ActionContextBuilder;
import com.ruleforge.dsl.builder.CriteriaContextBuilder;
import com.ruleforge.dsl.builder.LibraryContextBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * V5.44.1 — Standalone spring autoconfiguration for the legacy .ul DSL chain.
 *
 * <p>Replaces the 4 spring bean entries that used to live in
 * {@code ruleforge-core/src/main/resources/ruleforge-core-context.xml}
 * (ruleforge.dslRuleSetBuilder, ruleforge.actionContextBuilder,
 * ruleforge.criteriaContextBuilder, ruleforge.libraryContextBuilder). The bean
 * for {@code ruleforge.rulesRebuilder} stays in ruleforge-core — DSLRuleSetBuilder
 * still needs it (transitive compile dep on RulesRebuilder).
 *
 * @since 5.44
 */
@Configuration
public class RuleForgeDslAutoConfiguration {

    @Bean(name = "ruleforge.dslRuleSetBuilder")
    public DSLRuleSetBuilder dslRuleSetBuilder(
            @Qualifier("ruleforge.rulesRebuilder") RulesRebuilder rulesRebuilder) {
        DSLRuleSetBuilder builder = new DSLRuleSetBuilder();
        builder.setRulesRebuilder(rulesRebuilder);
        return builder;
    }

    @Bean(name = "ruleforge.actionContextBuilder")
    public ActionContextBuilder actionContextBuilder() {
        return new ActionContextBuilder();
    }

    @Bean(name = "ruleforge.criteriaContextBuilder")
    public CriteriaContextBuilder criteriaContextBuilder() {
        return new CriteriaContextBuilder();
    }

    @Bean(name = "ruleforge.libraryContextBuilder")
    public LibraryContextBuilder libraryContextBuilder() {
        return new LibraryContextBuilder();
    }
}
