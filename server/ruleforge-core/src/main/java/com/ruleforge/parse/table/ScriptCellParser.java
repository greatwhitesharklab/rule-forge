package com.ruleforge.parse.table;

import org.dom4j.Element;

import com.ruleforge.model.table.ScriptCell;
import com.ruleforge.parse.Parser;

/**
 * @author Jacky.gao
 * @since 2015年1月19日
 */
public class ScriptCellParser implements Parser<ScriptCell>{
	public ScriptCell parse(Element element) {
		ScriptCell cell=new ScriptCell();
		cell.setRow(Integer.valueOf(element.attributeValue("row")));
		cell.setCol(Integer.valueOf(element.attributeValue("col")));
		cell.setRowspan(Integer.valueOf(element.attributeValue("rowspan")));
		cell.setScript(element.getStringValue());
		return cell;
	}
	public boolean support(String name) {
		return name.equals("script-cell");
	}
}
