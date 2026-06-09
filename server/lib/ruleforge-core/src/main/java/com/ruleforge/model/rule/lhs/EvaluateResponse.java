package com.ruleforge.model.rule.lhs;

/**
 * @author Jacky.gao
 * @since 2017年11月17日
 */
public class EvaluateResponse {
	private boolean result;
	private Object leftResult;
	private Object rightResult;
	
	public void setLeftResult(Object leftResult) {
		this.leftResult = leftResult;
	}
	public void setRightResult(Object rightResult) {
		this.rightResult = rightResult;
	}
	public Object getLeftResult() {
		return leftResult;
	}
	public Object getRightResult() {
		return rightResult;
	}
	public void setResult(boolean result) {
		this.result = result;
	}
	public boolean getResult() {
		return result;
	}
}
