package com.ruleforge.model.rete;

import java.util.Map;

import com.ruleforge.runtime.rete.Activity;
import com.ruleforge.runtime.rete.OrActivity;


/**
 * @author Jacky.gao
 * @since 2015年1月6日
 */
public class OrNode extends JunctionNode {
	private NodeType nodeType=NodeType.or;
	public OrNode() {
		super(0);
	}
	public OrNode(int id) {
		super(id);
	}
	@Override
	public NodeType getNodeType() {
		return nodeType;
	}
	@Override
	public Activity newActivity(Map<Object,Object> context) {
		if(context.containsKey(this)){
			return (OrActivity)context.get(this);
		}
		OrActivity or=new OrActivity();
		for(Line line:lines){
			or.addPath(line.newPath(context));
		}
		context.put(this, or);
		return or;
	}
}
