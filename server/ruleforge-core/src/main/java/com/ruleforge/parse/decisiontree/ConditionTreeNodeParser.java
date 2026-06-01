package com.ruleforge.parse.decisiontree;

import java.util.ArrayList;
import java.util.List;

import org.dom4j.Element;

import com.ruleforge.model.decisiontree.ActionTreeNode;
import com.ruleforge.model.decisiontree.ConditionTreeNode;
import com.ruleforge.model.decisiontree.TreeNodeType;
import com.ruleforge.model.decisiontree.VariableTreeNode;
import com.ruleforge.model.rule.Op;
import com.ruleforge.parse.Parser;
import com.ruleforge.parse.ValueParser;

/**
 * @author Jacky.gao
 * @since 2016年2月26日
 */
public class ConditionTreeNodeParser implements Parser<ConditionTreeNode> {
	private ValueParser valueParser;
	private VariableTreeNodeParser variableTreeNodeParser;
	private ActionTreeNodeParser actionTreeNodeParser;
	@Override
	public ConditionTreeNode parse(Element element) {
		ConditionTreeNode node=new ConditionTreeNode();
		node.setNodeType(TreeNodeType.condition);
		String opAttr=element.attributeValue("op");
		if(opAttr!=null){
			node.setOp(Op.valueOf(opAttr));
		}
		List<ConditionTreeNode> conditionTreeNodes=new ArrayList<ConditionTreeNode>();
		List<ActionTreeNode> actionTreeNodes=new ArrayList<ActionTreeNode>();
		List<VariableTreeNode> variableTreeNodes=new ArrayList<VariableTreeNode>();
		for(Object obj:element.elements()){
			if(obj==null || !(obj instanceof Element)){
				continue;
			}
			Element ele=(Element)obj;
			String name=ele.getName();
			if(valueParser!=null && valueParser.support(name)){
				node.setValue(valueParser.parse(ele));
			}else if(support(name)){
				ConditionTreeNode cn=parse(ele);
				cn.setParentNode(node);
				conditionTreeNodes.add(cn);
			}else if(variableTreeNodeParser!=null && variableTreeNodeParser.support(name)){
				VariableTreeNode vn=variableTreeNodeParser.parse(ele);
				vn.setParentNode(node);
				variableTreeNodes.add(vn);
			}else if(actionTreeNodeParser!=null && actionTreeNodeParser.support(name)){
				ActionTreeNode an=actionTreeNodeParser.parse(ele);
				an.setParentNode(node);
				actionTreeNodes.add(an);
			}
		}
		if(conditionTreeNodes.size()>0){
			node.setConditionTreeNodes(conditionTreeNodes);
		}
		if(actionTreeNodes.size()>0){
			node.setActionTreeNodes(actionTreeNodes);
		}
		if(variableTreeNodes.size()>0){
			node.setVariableTreeNodes(variableTreeNodes);
		}
		return node;
	}
	
	public void setValueParser(ValueParser valueParser) {
		this.valueParser = valueParser;
	}
	
	public void setActionTreeNodeParser(
			ActionTreeNodeParser actionTreeNodeParser) {
		this.actionTreeNodeParser = actionTreeNodeParser;
	}
	public void setVariableTreeNodeParser(
			VariableTreeNodeParser variableTreeNodeParser) {
		this.variableTreeNodeParser = variableTreeNodeParser;
	}
	
	@Override
	public boolean support(String name) {
		return name.equals("condition-tree-node");
	}
}
