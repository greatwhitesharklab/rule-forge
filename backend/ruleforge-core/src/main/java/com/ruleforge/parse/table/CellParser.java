package com.ruleforge.parse.table;

import java.util.Collection;

import com.ruleforge.model.library.Datatype;
import org.apache.commons.lang.StringUtils;
import org.dom4j.Element;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.ruleforge.model.table.Cell;
import com.ruleforge.parse.ActionParser;
import com.ruleforge.parse.Parser;
import com.ruleforge.parse.ValueParser;

/**
 * @author Jacky.gao
 * @author fred
 * 2015年1月19日
 */
public class CellParser implements Parser<Cell>, ApplicationContextAware {
    private JointParser jointParser;
    private ValueParser valueParser;
    private Collection<ActionParser> actionParsers;

    public Cell parse(Element element) {
        Cell cell = new Cell();
        cell.setRow(Integer.valueOf(element.attributeValue("row")));
        cell.setCol(Integer.valueOf(element.attributeValue("col")));
        cell.setRowspan(Integer.valueOf(element.attributeValue("rowspan")));
        cell.setVariableLabel(element.attributeValue("var-label"));
        cell.setVariableName(element.attributeValue("var"));
        String datatype = element.attributeValue("datatype");
        if (StringUtils.isNotBlank(datatype)) {
            cell.setDatatype(Datatype.valueOf(datatype));
        }

        for (Object obj : element.elements()) {
            if (obj == null || !(obj instanceof Element)) {
                continue;
            }
            Element ele = (Element) obj;
            String name = ele.getName();
            if (jointParser != null && jointParser.support(name)) {
                cell.setJoint(jointParser.parse(ele));
            } else if (valueParser != null && valueParser.support(name)) {
                cell.setValue(valueParser.parse(ele));
            } else {
                if (actionParsers != null) {
                    for (ActionParser parser : actionParsers) {
                        if (parser.support(name)) {
                            cell.setAction(parser.parse(ele));
                            break;
                        }
                    }
                }
            }
        }
        return cell;
    }

    public boolean support(String name) {
        return name.equals("cell");
    }

    public void setJointParser(JointParser jointParser) {
        this.jointParser = jointParser;
    }

    public void setValueParser(ValueParser valueParser) {
        this.valueParser = valueParser;
    }

    public void setApplicationContext(ApplicationContext applicationContext)
            throws BeansException {
        actionParsers = applicationContext.getBeansOfType(ActionParser.class).values();
    }
}
