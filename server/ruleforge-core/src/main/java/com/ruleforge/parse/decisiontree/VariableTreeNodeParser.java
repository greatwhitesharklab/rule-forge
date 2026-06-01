package com.ruleforge.parse.decisiontree;

import java.util.ArrayList;
import java.util.List;

import org.dom4j.Element;

import com.ruleforge.model.decisiontree.ConditionTreeNode;
import com.ruleforge.model.decisiontree.TreeNodeType;
import com.ruleforge.model.decisiontree.VariableTreeNode;
import com.ruleforge.parse.LeftParser;
import com.ruleforge.parse.Parser;

/**
 * @author Jacky.gao
 * @since 2016年2月26日
 */
public class VariableTreeNodeParser implements Parser<VariableTreeNode> {
	private LeftParser leftParser;
	private ConditionTreeNodeParser conditionTreeNodeParser;
	@Override
	public VariableTreeNode parse(Element element) {
		VariableTreeNode node=new VariableTreeNode();
		node.setNodeType(TreeNodeType.variable);
		List<ConditionTreeNode> conditionTreeNodes=new ArrayList<ConditionTreeNode>();
		for(Object obj:element.elements()){
			if(obj==null || !(obj instanceof Element)){
				continue;
			}
			Element ele=(Element)obj;
			String name=ele.getName();
			if(name.equals("left")){
				node.setLeft(leftParser.parse(ele));
			}else if(conditionTreeNodeParser.support(name)){
				ConditionTreeNode cn=conditionTreeNodeParser.parse(ele);
				cn.setParentNode(node);
				conditionTreeNodes.add(cn);
			}
		}
		if(conditionTreeNodes.size()>0){
			node.setConditionTreeNodes(conditionTreeNodes);
		}
		return node;
	}
	
	public void setConditionTreeNodeParser(
			ConditionTreeNodeParser conditionTreeNodeParser) {
		this.conditionTreeNodeParser = conditionTreeNodeParser;
	}
	
	public void setLeftParser(LeftParser leftParser) {
		this.leftParser = leftParser;
	}
	
	@Override
	public boolean support(String name) {
		return name.equals("variable-tree-node");
	}
}
