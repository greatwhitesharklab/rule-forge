package com.ruleforge.model.rule;
/**
 * @author Jacky.gao
 * @since 2014年12月25日
 */
public class ComplexArithmetic extends Arithmetic {
	private Value value;
	public Value getValue() {
		return value;
	}
	public void setValue(Value value) {
		this.value = value;
	}
	@Override
	public String getId() {
		String id=type.toString()+value.getId();
		return id;
	}
}
