package com.ruleforge.model.table;

import com.ruleforge.model.rule.Op;
import com.ruleforge.model.rule.Value;

/**
 * @author Jacky.gao
 * @since 2015年1月19日
 */
public class Condition {
	private Op op;
	private Value value;
	public Op getOp() {
		return op;
	}
	public void setOp(Op op) {
		this.op = op;
	}
	public Value getValue() {
		return value;
	}
	public void setValue(Value value) {
		this.value = value;
	}
}
