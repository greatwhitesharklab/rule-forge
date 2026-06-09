package com.ruleforge.model.rete;

import java.util.Map;

import com.ruleforge.runtime.rete.Activity;
import com.ruleforge.runtime.rete.AndActivity;

/**
 * @author Jacky.gao
 * @since 2015年1月6日
 */
public class AndNode extends JunctionNode {
	private NodeType nodeType=NodeType.and;
	public AndNode() {
		super(0);
	}
	public AndNode(int id) {
		super(id);
	}
	@Override
	public NodeType getNodeType() {
		return nodeType;
	}
	public void setToLineCount(int toLineCount){
		this.toLineCount=toLineCount;
	}
	@Override
	public Activity newActivity(Map<Object,Object> context) {
		if(context.containsKey(this)){
			return (AndActivity)context.get(this);
		}
		AndActivity and=new AndActivity();
		for(Line line:lines){
			and.addPath(line.newPath(context));
		}
		context.put(this, and);
		return and;
	}
}
