package com.ruleforge.dsl.builder;

import org.antlr.v4.runtime.ParserRuleContext;

/**
 * @author Jacky.gao
 * @since 2015年2月15日
 */
public interface ContextBuilder {
	Object build(ParserRuleContext context);
	boolean support(ParserRuleContext context);
}
