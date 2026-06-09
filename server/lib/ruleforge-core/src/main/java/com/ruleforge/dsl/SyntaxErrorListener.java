package com.ruleforge.dsl;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/**
 * @author Jacky.gao
 * @since 2015年2月27日
 */
public class SyntaxErrorListener extends BaseErrorListener {
	private SyntaxErrorReportor reportor;
	public SyntaxErrorListener(SyntaxErrorReportor reportor) {
		this.reportor=reportor;
	}
	@Override
	public void syntaxError(Recognizer<?, ?> recognizer,
			Object offendingSymbol, int line, int charPositionInLine,
			String msg, RecognitionException e) {
		this.reportor.addError(line, charPositionInLine, offendingSymbol, msg);
		//super.syntaxError(recognizer, offendingSymbol, line, charPositionInLine, msg, e);
	}
}
