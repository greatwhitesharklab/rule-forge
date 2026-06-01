package com.ruleforge.model.rule;
/**
 * @author Jacky.gao
 * @since 2015年6月14日
 */
public class ParenValue extends AbstractValue {
	private Value value;
	@Override
	public String getId() {
		String id="(";
		if(value!=null){
			id+=value.getId();
		}
		id+=")";
		if(arithmetic!=null){
			id=id+arithmetic.getId();
		}
		return id;
	}
	@Override
	public ValueType getValueType() {
		return ValueType.Paren;
	}
	public Value getValue() {
		return value;
	}
	public void setValue(Value value) {
		this.value = value;
	}
}
