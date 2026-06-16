package com.ruleforge.parse.crosstab;

import com.ruleforge.model.crosstab.ValueCrossCell;
import com.ruleforge.parse.Parser;
import com.ruleforge.parse.ValueParser;
import org.dom4j.Element;

/**
 * @author fred
 * @since 2018-11-05 6:51 PM
 */
public class ValueCrossCellParser extends CrossCellParser implements Parser<ValueCrossCell> {
    private ValueParser valueParser;

    public ValueCrossCellParser() {
    }

    public ValueCrossCell parse(Element element) {
        ValueCrossCell cell = new ValueCrossCell();
        this.parseCrossCell(cell, element);
        // V5.96 — Iterator var123 → enhanced for (break 仍 work)
        for (Object obj : element.elements()) {
            if (obj != null && obj instanceof Element) {
                Element ele = (Element) obj;
                if (this.valueParser.support(ele.getName())) {
                    cell.setValue(this.valueParser.parse(ele));
                    break;
                }
            }
        }

        return cell;
    }

    public boolean support(String name) {
        return "value-cell".equals(name);
    }

    public void setValueParser(ValueParser valueParser) {
        this.valueParser = valueParser;
    }
}
