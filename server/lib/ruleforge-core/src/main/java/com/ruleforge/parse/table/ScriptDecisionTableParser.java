package com.ruleforge.parse.table;

import org.dom4j.Element;

import com.ruleforge.model.rule.Library;
import com.ruleforge.model.rule.LibraryType;
import com.ruleforge.model.table.ScriptDecisionTable;
import com.ruleforge.parse.Parser;

/**
 * @author Jacky.gao
 * @since 2015年1月19日
 */
public class ScriptDecisionTableParser implements Parser<ScriptDecisionTable> {
	private RowParser rowParser;
	private ColumnParser columnParser;
	private ScriptCellParser scriptCellParser;
	public ScriptDecisionTable parse(Element element) {
		ScriptDecisionTable table =new ScriptDecisionTable();
		for(Object obj:element.elements()){
			if(obj==null || !(obj instanceof Element)){
				continue;
			}
			Element ele=(Element)obj;
			String name=ele.getName();
			if(rowParser.support(name)){
				table.addRow(rowParser.parse(ele));
			}else if(columnParser.support(name)){
				table.addColumn(columnParser.parse(ele));
			}else if(scriptCellParser.support(name)){
				table.addCell(scriptCellParser.parse(ele));
			}if(name.equals("import-variable-library")){
				table.addLibrary(new Library(ele.attributeValue("path"),null,LibraryType.Variable));
			}else if(name.equals("import-constant-library")){
				table.addLibrary(new Library(ele.attributeValue("path"),null,LibraryType.Constant));
			}else if(name.equals("import-action-library")){
				table.addLibrary(new Library(ele.attributeValue("path"),null,LibraryType.Action));
			}else if(name.equals("import-parameter-library")){
				table.addLibrary(new Library(ele.attributeValue("path"),null,LibraryType.Parameter));
			}
		}
		return table;
	}
	public boolean support(String name) {
		return name.equals("script-decision-table");
	}
	public void setColumnParser(ColumnParser columnParser) {
		this.columnParser = columnParser;
	}
	public void setRowParser(RowParser rowParser) {
		this.rowParser = rowParser;
	}
	public void setScriptCellParser(ScriptCellParser scriptCellParser) {
		this.scriptCellParser = scriptCellParser;
	}
}
