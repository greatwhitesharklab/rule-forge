package com.ruleforge.model.rete;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * @author Jacky.gao
 * @since 2015年1月6日
 */
public abstract class JunctionNode extends BaseReteNode{
	protected int toLineCount;
	@JsonIgnore
	protected List<Line> toConnections=new ArrayList<Line>();
	public JunctionNode(int id) {
		super(id);
	}
	public List<Line> getToConnections() {
		return toConnections;
	}
	public void addToConnection(Line connection){
		this.toConnections.add(connection);
		toLineCount=this.toConnections.size();
	}
	public int getToLineCount() {
		return toLineCount;
	}
}
