package com.ruleforge.parse.scorecard;

import org.apache.commons.lang.StringUtils;
import org.dom4j.Element;

import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.scorecard.CardCell;
import com.ruleforge.model.scorecard.CellType;
import com.ruleforge.parse.Parser;
import com.ruleforge.parse.ValueParser;
import com.ruleforge.parse.table.JointParser;

/**
 * @author Jacky.gao
 * @since 2016年9月22日
 */
public class CardCellParser implements Parser<CardCell> {
	private ValueParser valueParser;
	private JointParser jointParser;
	@Override
	public CardCell parse(Element element) {
		CardCell cell=new CardCell();
		cell.setType(CellType.valueOf(element.attributeValue("type")));
		cell.setCol(Integer.valueOf(element.attributeValue("col")));
		cell.setRow(Integer.valueOf(element.attributeValue("row")));
		String datatype=element.attributeValue("datatype");
		if(StringUtils.isNotBlank(datatype)){
			cell.setDatatype(Datatype.valueOf(datatype));
		}
		cell.setVariableName(element.attributeValue("var"));
		cell.setVariableLabel(element.attributeValue("var-label"));
		cell.setVariableCategory(element.attributeValue("category"));
		cell.setWeight(element.attributeValue("weight"));
		for(Object obj:element.elements()){
			if(obj==null || !(obj instanceof Element)){
				continue;
			}
			Element ele=(Element)obj;
			if(valueParser.support(ele.getName())){
				cell.setValue(valueParser.parse(ele));
			}else if(jointParser.support(ele.getName())){
				cell.setJoint(jointParser.parse(ele));
			}
		}
		return cell;
	}
	
	public void setJointParser(JointParser jointParser) {
		this.jointParser = jointParser;
	}
	public void setValueParser(ValueParser valueParser) {
		this.valueParser = valueParser;
	}
	
	@Override
	public boolean support(String name) {
		return name.equals("card-cell");
	}
}
