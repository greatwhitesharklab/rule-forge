package com.ruleforge.parse.table;

import org.apache.commons.lang.StringUtils;
import org.dom4j.Element;

import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.table.Column;
import com.ruleforge.model.table.ColumnType;
import com.ruleforge.parse.Parser;

/**
 * @author Jacky.gao
 * @since 2015年1月19日
 */
public class ColumnParser implements Parser<Column> {
	public Column parse(Element element) {
		Column col=new Column();
		col.setNum(Integer.valueOf(element.attributeValue("num")));
		col.setType(ColumnType.valueOf(element.attributeValue("type")));
		col.setVariableCategory(element.attributeValue("var-category"));
		col.setVariableLabel(element.attributeValue("var-label"));
		col.setVariableName(element.attributeValue("var"));
		col.setWidth(Integer.valueOf(element.attributeValue("width")));
		String datatype=element.attributeValue("datatype");
		if(StringUtils.isNotEmpty(datatype)){
			col.setDatatype(Datatype.valueOf(datatype));			
		}
		return col;
	}
	public boolean support(String name) {
		return name.equals("col");
	}
}
