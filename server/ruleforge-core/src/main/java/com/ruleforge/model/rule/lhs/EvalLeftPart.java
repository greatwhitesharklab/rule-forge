package com.ruleforge.model.rule.lhs;
/**
 * @author Jacky.gao
 * @since 2015年3月15日
 */
public class EvalLeftPart implements LeftPart {
	private String id;
	private String expression;
	@Override
	public String getId() {
		if(id==null){
			id = "[eval]"+expression+"";				
		}
		return id;
	}
	public String getExpression() {
		return expression;
	}
	public void setExpression(String expression) {
		this.expression = expression;
	}
}
