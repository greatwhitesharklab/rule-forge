package com.ruleforge.builder.table;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;

import com.ruleforge.exception.RuleException;
import com.ruleforge.dsl.CellScriptRuleParserBaseVisitor;
import com.ruleforge.dsl.RuleParserLexer;
import com.ruleforge.dsl.RuleParserParser;
import com.ruleforge.dsl.ScriptDecisionTableErrorListener;

/**
 * @author Jacky.gao
 * @since 2015年5月6日
 */
public class CellScriptDSLBuilder {
	public String buildCriteriaScript(String script,String propertyName){
		ANTLRInputStream antlrInputStream=new ANTLRInputStream(script);
		RuleParserLexer lexer=new RuleParserLexer(antlrInputStream);
		CommonTokenStream tokenStream=new CommonTokenStream(lexer);
		RuleParserParser parser=new RuleParserParser(tokenStream);
		ScriptDecisionTableErrorListener errorListener=new ScriptDecisionTableErrorListener();
		parser.addErrorListener(errorListener);
		CellScriptRuleParserBaseVisitor visitor=new CellScriptRuleParserBaseVisitor(propertyName);
		String resultScript=visitor.visit(parser.decisionTableCellCondition());
		String error=errorListener.getErrorMessage();
		if(error!=null){
			throw new RuleException("Script Parse error:"+error);
		}
		return resultScript;
	}
}
