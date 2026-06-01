package com.ruleforge.model.rule.lhs;

import java.math.BigDecimal;
import java.util.List;

import com.ruleforge.runtime.rete.EvaluationContext;

/**
 * @author Jacky.gao
 * @since 2015年5月29日
 */
public class ExistLeftPart extends AllLeftPart {
	public boolean evaluate(EvaluationContext context,Object obj,List<Object> allMatchedObjects){
		ExprValue value=computeValue(context, obj, allMatchedObjects);
		int total=value.getTotal(),match=value.getMatch();
		switch(statisticType){
		case none:
			if(match>0){
				return true;
			}else{
				return false;
			}
		case amount:
			if(match>=amount){
				return true;
			}else{
				return false;
			}
		case percent:
			BigDecimal left=new BigDecimal(match);
			BigDecimal currentPercent=left.divide(new BigDecimal(total),4,BigDecimal.ROUND_HALF_UP);
			int result=currentPercent.compareTo((new BigDecimal(percent)).divide(new BigDecimal(100)));
			if(result!=-1){
				return true;
			}else{
				return false;
			}
		}
		return false;
	}
	@Override
	public String getId() {
		if(id==null){
			id="all("+variableCategory+"."+variableLabel+","+multiCondition.getId();
			if(statisticType.equals(StatisticType.amount)){
				id+=","+amount+")";
			}else if(statisticType.equals(StatisticType.percent)){
				id+=","+percent+"%)";
			}else{
				id+=")";
			}
		}
		return id;
	}
}
