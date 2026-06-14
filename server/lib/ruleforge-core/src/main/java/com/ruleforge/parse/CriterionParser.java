package com.ruleforge.parse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.dom4j.Element;
import com.ruleforge.plugin.EnginePluginRegistry;

import com.ruleforge.model.rule.lhs.Criterion;
/**
 * @author Jacky.gao
 * @since 2014年12月23日
 */
public abstract class CriterionParser extends AbstractParser<Criterion> {
	protected Collection<CriterionParser> criterionParsers;
	
	protected List<Criterion> parseCriterion(Element element){
		List<Criterion> list=null;
		for(Object obj:element.elements()){
			if(obj==null || !(obj instanceof Element)){
				continue;
			}
			Element ele=(Element)obj;
			String name=ele.getName();
			for(CriterionParser parser:criterionParsers){
				if(parser.support(name)){
					if(list==null)list=new ArrayList<Criterion>();
					Criterion criterion=parser.parse(ele);
					if(criterion!=null){
						list.add(criterion);						
					}
					break;
				}
			}
		}
		return list;
	}
		
	public void setPluginRegistry(EnginePluginRegistry pluginRegistry) {
		this.criterionParsers=pluginRegistry.getCriterionParsers();
	}
}
