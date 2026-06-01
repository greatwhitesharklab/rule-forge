package com.ruleforge.parse.table;

import org.dom4j.Element;

import com.ruleforge.model.table.Row;
import com.ruleforge.parse.Parser;

/**
 * @author Jacky.gao
 * @author fred
 * 2015年1月19日
 */
public class RowParser implements Parser<Row> {
    public Row parse(Element element) {
        Row row = new Row();
        row.setHeight(Integer.valueOf(element.attributeValue("height")));
        row.setNum(Integer.valueOf(element.attributeValue("num")));
        return row;
    }

    public boolean support(String name) {
        return name.equals("row");
    }
}
