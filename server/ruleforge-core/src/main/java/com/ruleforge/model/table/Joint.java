package com.ruleforge.model.table;

import java.util.ArrayList;
import java.util.List;

import com.ruleforge.model.rule.lhs.And;
import com.ruleforge.model.rule.lhs.Junction;
import com.ruleforge.model.rule.lhs.Or;

/**
 * @author Jacky.gao
 * @since 2015年1月19日
 */
public class Joint {
	private List<Condition> conditions;
	private List<Joint> joints;
	private JointType type;
	public Junction getJunction(){
		if(type.equals(JointType.and)){
			return new And();
		}else{
			return new Or();
		}
	}
	public List<Condition> getConditions() {
		return conditions;
	}
	public void setConditions(List<Condition> conditions) {
		this.conditions = conditions;
	}
	public void addJoint(Joint joint){
		if(joints==null){
			joints=new ArrayList<Joint>();
		}
		joints.add(joint);
	}
	public void addCondition(Condition condition){
		if(conditions==null){
			conditions=new ArrayList<Condition>();
		}
		conditions.add(condition);
	}
	public List<Joint> getJoints() {
		return joints;
	}
	public void setJoints(List<Joint> joints) {
		this.joints = joints;
	}
	public JointType getType() {
		return type;
	}
	public void setType(JointType type) {
		this.type = type;
	}
}
