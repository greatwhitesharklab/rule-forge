package com.ruleforge.dsl;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/**
 * @author Jacky.gao
 * @since 2015年5月11日
 */
public class ScriptDecisionTableErrorListener extends BaseErrorListener {
	private StringBuffer sb;
	@Override
	public void syntaxError(Recognizer<?, ?> recognizer,Object offendingSymbol, 
			int line, int charPositionInLine,
			String msg, RecognitionException e) {
		if(sb==null){
			sb=new StringBuffer();
		}
		sb.append("["+offendingSymbol+"] is invalid:"+msg);
		sb.append("\r\n");
	}
	public String getErrorMessage(){
		if(sb==null){
			return null;
		}
		return sb.toString();
	}
}
