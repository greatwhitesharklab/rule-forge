package com.ruleforge.model.rule;
/**
 * @author Jacky.gao
 * @since 2014年12月25日
 */
public abstract class Arithmetic {
	protected ArithmeticType type;
	public ArithmeticType getType() {
		return type;
	}
	public void setType(ArithmeticType type) {
		this.type = type;
	}
	public abstract String getId();
}
