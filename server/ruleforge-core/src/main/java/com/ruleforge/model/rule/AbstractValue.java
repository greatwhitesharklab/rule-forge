package com.ruleforge.model.rule;
/**
 * @author Jacky.gao
 * @since 2014年12月29日
 */
public abstract class AbstractValue implements Value {
	protected ComplexArithmetic arithmetic;
	public ComplexArithmetic getArithmetic() {
		return arithmetic;
	}

	public void setArithmetic(ComplexArithmetic arithmetic) {
		this.arithmetic = arithmetic;
	}
}
