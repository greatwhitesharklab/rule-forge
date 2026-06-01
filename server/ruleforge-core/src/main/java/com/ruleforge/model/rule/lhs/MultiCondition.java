package com.ruleforge.model.rule.lhs;

import java.util.ArrayList;
import java.util.List;

import com.ruleforge.runtime.rete.EvaluationContext;

/**
 * @author Jacky.gao
 * @since 2015年5月29日
 */
public class MultiCondition {
	private String id;
	private List<PropertyCriteria> conditions;
	private JunctionType type=JunctionType.and;
	public boolean evaluate(EvaluationContext context,Object obj,List<Object> allMatchedObjects){
		if(type.equals(JunctionType.and)){
			for(PropertyCriteria criteria:conditions){
				boolean value=criteria.evaluate(context, obj, allMatchedObjects);
				if(!value){
					return false;
				}
			}
		}else{
			for(PropertyCriteria criteria:conditions){
				boolean value=criteria.evaluate(context, obj, allMatchedObjects);
				if(value){
					break;
				}
			}
		}
		return true;
	}
	public void addCondition(PropertyCriteria condition){
		if(conditions==null){
			conditions=new ArrayList<PropertyCriteria>();
		}
		conditions.add(condition);
	}
	public List<PropertyCriteria> getConditions() {
		return conditions;
	}

	public void setConditions(List<PropertyCriteria> conditions) {
		this.conditions = conditions;
	}

	public JunctionType getType() {
		return type;
	}
	public void setType(JunctionType type) {
		this.type = type;
	}
	public String getId(){
		if(id==null){
			for(PropertyCriteria criteria:conditions){
				if(id==null){
					id=criteria.getId();
				}else{
					id+=type.name()+" "+criteria.getId();
				}
			}
		}
		return id;
	}
}
