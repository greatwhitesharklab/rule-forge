package com.ruleforge.model.rule;

import com.ruleforge.model.library.Datatype;

/**
 * @author Jacky.gao
 * @since 2016年8月16日
 */
public class NamedReferenceValue extends AbstractValue{
	private String referenceName;
	private String propertyLabel;
	private String propertyName;
	private Datatype datatype;
	
	@Override
	public String getId() {
		String id="[REF]"+referenceName+"."+propertyLabel;
		if(arithmetic!=null){
			id+=arithmetic.getId();
		}
		return id;
	}
	@Override
	public ValueType getValueType() {
		return ValueType.NamedReference;
	}
	public String getReferenceName() {
		return referenceName;
	}
	public void setReferenceName(String referenceName) {
		this.referenceName = referenceName;
	}
	public String getPropertyLabel() {
		return propertyLabel;
	}
	public void setPropertyLabel(String propertyLabel) {
		this.propertyLabel = propertyLabel;
	}
	public String getPropertyName() {
		return propertyName;
	}
	public void setPropertyName(String propertyName) {
		this.propertyName = propertyName;
	}
	public Datatype getDatatype() {
		return datatype;
	}
	public void setDatatype(Datatype datatype) {
		this.datatype = datatype;
	}
}
