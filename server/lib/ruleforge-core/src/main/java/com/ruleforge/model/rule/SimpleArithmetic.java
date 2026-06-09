package com.ruleforge.model.rule;
/**
 * @author Jacky.gao
 * @since 2014年12月25日
 */
public class SimpleArithmetic extends Arithmetic {
	private SimpleArithmeticValue value;

	public SimpleArithmeticValue getValue() {
		return value;
	}

	public void setValue(SimpleArithmeticValue value) {
		this.value = value;
	}
	@Override
	public String getId() {
		String id=type.toString()+value.getId();
		return id;
	}
}
