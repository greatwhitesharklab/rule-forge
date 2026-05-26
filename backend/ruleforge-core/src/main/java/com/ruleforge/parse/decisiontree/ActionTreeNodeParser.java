package com.ruleforge.parse.decisiontree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.dom4j.Element;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.ruleforge.action.Action;
import com.ruleforge.model.decisiontree.ActionTreeNode;
import com.ruleforge.model.decisiontree.TreeNodeType;
import com.ruleforge.parse.ActionParser;
import com.ruleforge.parse.Parser;

/**
 * @author Jacky.gao
 * @since 2016年2月26日
 */
public class ActionTreeNodeParser implements Parser<ActionTreeNode>,ApplicationContextAware {
	private Collection<ActionParser> actionParsers;
	@Override
	public ActionTreeNode parse(Element element) {
		ActionTreeNode node=new ActionTreeNode();
		node.setNodeType(TreeNodeType.action);
		List<Action> actions=new ArrayList<Action>();
		for(Object obj:element.elements()){
			if(obj==null || !(obj instanceof Element)){
				continue;
			}
			Element ele=(Element)obj;
			String name=ele.getName();

			if(actionParsers==null){
				continue;
			}
			for(ActionParser actionParser:actionParsers){
				if(actionParser.support(name)){
					actions.add(actionParser.parse(ele));
					break;
				}
			}
		}
		node.setActions(actions);
		return node;
	}
	
	@Override
	public boolean support(String name) {
		return name.equals("action-tree-node");
	}
	public void setApplicationContext(ApplicationContext context) throws BeansException {
		actionParsers=context.getBeansOfType(ActionParser.class).values();
	}
}
