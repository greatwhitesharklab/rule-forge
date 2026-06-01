package com.ruleforge.parse.scorecard;

import java.util.ArrayList;
import java.util.List;

import org.dom4j.Element;

import com.ruleforge.model.scorecard.AttributeRow;
import com.ruleforge.model.scorecard.ConditionRow;
import com.ruleforge.parse.Parser;

/**
 * @author Jacky.gao
 * @since 2016年9月22日
 */
public class AttributeRowParser implements Parser<AttributeRow> {
	@Override
	public AttributeRow parse(Element element) {
		AttributeRow row=new AttributeRow();
		row.setRowNumber(Integer.valueOf(element.attributeValue("row-number")));
		List<ConditionRow> rows=new ArrayList<ConditionRow>();
		for(Object obj:element.elements()){
			if(obj==null || !(obj instanceof Element)){
				continue;
			}
			Element ele=(Element)obj;
			if(ele.getName().equals("condition-row")){
				ConditionRow r=new ConditionRow();
				r.setRowNumber(Integer.valueOf(ele.attributeValue("row-number")));
				rows.add(r);
			}
		}
		row.setConditionRows(rows);
		return row;
	}
	@Override
	public boolean support(String name) {
		return name.equals("attribute-row");
	}
}
