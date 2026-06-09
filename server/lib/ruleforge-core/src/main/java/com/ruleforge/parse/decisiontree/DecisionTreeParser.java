package com.ruleforge.parse.decisiontree;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.dom4j.Element;

import com.ruleforge.Configure;
import com.ruleforge.exception.RuleException;
import com.ruleforge.action.Action;
import com.ruleforge.builder.RulesRebuilder;
import com.ruleforge.model.decisiontree.ActionTreeNode;
import com.ruleforge.model.decisiontree.ConditionTreeNode;
import com.ruleforge.model.decisiontree.DecisionTree;
import com.ruleforge.model.decisiontree.TreeNode;
import com.ruleforge.model.decisiontree.VariableTreeNode;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.rule.Library;
import com.ruleforge.model.rule.LibraryType;
import com.ruleforge.model.rule.Value;
import com.ruleforge.model.rule.lhs.Left;
import com.ruleforge.model.rule.lhs.LeftPart;
import com.ruleforge.model.rule.lhs.VariableLeftPart;
import com.ruleforge.parse.Parser;

/**
 * @author Jacky.gao
 * @since 2016年2月26日
 */
public class DecisionTreeParser implements Parser<DecisionTree> {
	private VariableTreeNodeParser variableTreeNodeParser;
	private RulesRebuilder rulesRebuilder;
	@Override
	public DecisionTree parse(Element element) {
		DecisionTree tree=new DecisionTree();
		
		String salience=element.attributeValue("salience");
		if(StringUtils.isNotEmpty(salience)){
			tree.setSalience(Integer.valueOf(salience));
		}
		String effectiveDate=element.attributeValue("effective-date");
		SimpleDateFormat sd=new SimpleDateFormat(Configure.getDateFormat());
		if(StringUtils.isNotEmpty(effectiveDate)){
			try {
				tree.setEffectiveDate(sd.parse(effectiveDate));
			} catch (ParseException e) {
				throw new RuleException(e);
			}
		}
		String expiresDate=element.attributeValue("expires-date");
		if(StringUtils.isNotEmpty(expiresDate)){
			try {
				tree.setExpiresDate(sd.parse(expiresDate));
			} catch (ParseException e) {
				throw new RuleException(e);
			}
		}
		String enabled=element.attributeValue("enabled");
		if(StringUtils.isNotEmpty(enabled)){
			tree.setEnabled(Boolean.valueOf(enabled));
		}
		
		String debug=element.attributeValue("debug");
		if(StringUtils.isNotEmpty(debug)){
			tree.setDebug(Boolean.valueOf(debug));
		}
		
		List<Library> libs=new ArrayList<Library>();
		for(Object obj:element.elements()){
			if(obj==null || !(obj instanceof Element)){
				continue;
			}
			Element ele=(Element)obj;
			String name=ele.getName();
			if(variableTreeNodeParser.support(name)){
				tree.setVariableTreeNode(variableTreeNodeParser.parse(ele));
			}if(name.equals("import-variable-library")){
				libs.add(new Library(ele.attributeValue("path"),null,LibraryType.Variable));
			}else if(name.equals("import-constant-library")){
				libs.add(new Library(ele.attributeValue("path"),null,LibraryType.Constant));
			}else if(name.equals("import-action-library")){
				libs.add(new Library(ele.attributeValue("path"),null,LibraryType.Action));
			}else if(name.equals("import-parameter-library")){
				libs.add(new Library(ele.attributeValue("path"),null,LibraryType.Parameter));
			}else if(name.equals("remark")){
				tree.setRemark(ele.getText());
			}
		}
		tree.setLibraries(libs);
		ResourceLibrary resourceLibrary = rulesRebuilder.getResourceLibraryBuilder().buildResourceLibrary(libs);
		rebuildTreeNode(resourceLibrary,tree.getVariableTreeNode());
		return tree;
	}
	
	private void rebuildTreeNode(ResourceLibrary resourceLibrary,TreeNode treeNode){
		if(treeNode==null)return;
		if(treeNode instanceof VariableTreeNode){
			VariableTreeNode varNode=(VariableTreeNode)treeNode;
			Left left=varNode.getLeft();
			if(left!=null){
				LeftPart part=left.getLeftPart();
				if(part!=null && part instanceof VariableLeftPart){
					VariableLeftPart varPart=(VariableLeftPart)part;
					String category=varPart.getVariableCategory();
					String name=varPart.getVariableName();
					if(StringUtils.isNotBlank(category) && StringUtils.isNotBlank(name)){
						Variable var=rulesRebuilder.getVariableByName(resourceLibrary.getVariableCategories(), category, name, null);
						varPart.setDatatype(var.getType());
						varPart.setVariableLabel(var.getLabel());
					}
				}
			}
			List<ConditionTreeNode> nodes=varNode.getConditionTreeNodes();
			if(nodes!=null){
				for(ConditionTreeNode node:nodes){
					rebuildTreeNode(resourceLibrary, node);
				}				
			}
		}else if(treeNode instanceof ConditionTreeNode){
			ConditionTreeNode node=(ConditionTreeNode)treeNode;
			Value value=node.getValue();
			if(value!=null){
				rulesRebuilder.rebuildValue(value, resourceLibrary, null, false);
			}
			List<ActionTreeNode> actionNodes=node.getActionTreeNodes();
			if(actionNodes!=null){
				for(ActionTreeNode actionNode:actionNodes){
					rebuildTreeNode(resourceLibrary, actionNode);
				}
			}
			List<ConditionTreeNode> conditionNodes=node.getConditionTreeNodes();
			if(conditionNodes!=null){				
				for(ConditionTreeNode conditionNode:conditionNodes){
					rebuildTreeNode(resourceLibrary, conditionNode);
				}
			}
			List<VariableTreeNode> varNodes=node.getVariableTreeNodes();
			if(varNodes!=null){
				for(VariableTreeNode varNode:varNodes){
					rebuildTreeNode(resourceLibrary, varNode);
				}
			}
		}else if(treeNode instanceof ActionTreeNode){
			ActionTreeNode actionNode=(ActionTreeNode)treeNode;
			List<Action> actions=actionNode.getActions();
			if(actions!=null){
				for(Action action:actions){
					if(action==null){
						continue;
					}
					rulesRebuilder.rebuildAction(action, resourceLibrary, null, false);
				}
			}
		}
	}
	
	public void setVariableTreeNodeParser(
			VariableTreeNodeParser variableTreeNodeParser) {
		this.variableTreeNodeParser = variableTreeNodeParser;
	}
	@Override
	public boolean support(String name) {
		return name.equals("decision-tree");
	}
	
	public void setRulesRebuilder(RulesRebuilder rulesRebuilder) {
		this.rulesRebuilder = rulesRebuilder;
	}
}
