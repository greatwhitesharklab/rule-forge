package com.ruleforge.dsl;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Jacky.gao
 * @since 2015年2月27日
 */
public class SyntaxErrorReportor {
	private List<String> errorList=new ArrayList<String>();
	public void addError(int line,int charPositionInLine,Object offendingSymbol,String msg){
		errorList.add(line+"行,"+charPositionInLine+"列，"+offendingSymbol+"字符处，存在语法错误:"+msg);
	}
	public String getSyntaxErrorMessage(){
		StringBuffer sb=new StringBuffer();
		for(String msg:errorList){
			sb.append(msg);
			sb.append("\n");
		}
		return sb.toString();
	}
}
