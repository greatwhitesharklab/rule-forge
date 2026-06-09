package com.ruleforge.parse;

import java.util.List;

import org.dom4j.Element;

import com.ruleforge.model.rule.lhs.And;
import com.ruleforge.model.rule.lhs.Criterion;
import com.ruleforge.model.rule.lhs.Or;
/**
 * @author Jacky.gao
 * @since 2014年12月23日
 */
public class JunctionParser extends CriterionParser {
	public Criterion parse(Element element) {
		List<Criterion> list=parseCriterion(element);
		if(list==null || list.size()==0){
			return null;
		}
		String name=element.getName();
		if(name.equals("and")){
			And and=new And();
			and.setCriterions(list);
			return and;
		}else{
			Or or=new Or();
			or.setCriterions(list);
			return or;
		}
	}

	public boolean support(String name) {
		return name.equals("and") || name.equals("or");
	}
}
