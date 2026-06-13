package com.ruleforge.dsl.builder;

import org.antlr.v4.runtime.ParserRuleContext;

import com.ruleforge.exception.RuleException;
import com.ruleforge.dsl.RuleParserParser.ResourceContext;
import com.ruleforge.model.rule.Library;
import com.ruleforge.model.rule.LibraryType;

/**
 * @author Jacky.gao
 * @since 2015年2月15日
 */
public class LibraryContextBuilder extends AbstractContextBuilder {

	@Override
	public Library build(ParserRuleContext context) {
		ResourceContext ctx=(ResourceContext)context;
		if(ctx.importActionLibrary()!=null){
			String path=BuildUtils.getSTRINGContent(ctx.importActionLibrary().STRING());
			return new Library(path,null,LibraryType.Action);
		}else if(ctx.importConstantLibrary()!=null){
			String path=BuildUtils.getSTRINGContent(ctx.importConstantLibrary().STRING());
			return new Library(path,null,LibraryType.Constant);
		}else if(ctx.importVariableLibrary()!=null){
			String path=BuildUtils.getSTRINGContent(ctx.importVariableLibrary().STRING());
			return new Library(path,null,LibraryType.Variable);
		}else if(ctx.importParameterLibrary()!=null){
			String path=BuildUtils.getSTRINGContent(ctx.importParameterLibrary().STRING());
			return new Library(path,null,LibraryType.Parameter);
		}
		throw new RuleException("Unsupport context "+ctx.getClass().getName()+"");
	}

	@Override
	public boolean support(ParserRuleContext context) {
		return context instanceof ResourceContext;
	}
}
