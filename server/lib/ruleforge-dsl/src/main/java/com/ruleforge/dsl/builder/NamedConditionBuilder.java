package com.ruleforge.dsl.builder;

import java.util.ArrayList;
import java.util.List;

import com.ruleforge.exception.RuleException;
import com.ruleforge.dsl.DSLUtils;
import com.ruleforge.dsl.RuleParserParser.JoinContext;
import com.ruleforge.dsl.RuleParserParser.MultiNamedConditionsContext;
import com.ruleforge.dsl.RuleParserParser.NamedConditionContext;
import com.ruleforge.dsl.RuleParserParser.ParenNamedConditionsContext;
import com.ruleforge.dsl.RuleParserParser.SingleNamedConditionsContext;
import com.ruleforge.model.rule.Op;
import com.ruleforge.model.rule.lhs.Criteria;
import com.ruleforge.model.rule.lhs.CriteriaUnit;
import com.ruleforge.model.rule.lhs.JunctionType;
import com.ruleforge.model.rule.lhs.Left;
import com.ruleforge.model.rule.lhs.LeftType;
import com.ruleforge.model.rule.lhs.VariableLeftPart;

/**
 * @author Jacky.gao
 * @since 2016年8月15日
 */
public class NamedConditionBuilder {
	public CriteriaUnit buildNamedCriteria(NamedConditionContext namedConditionContext,String variableCategory){
		CriteriaUnit unit=null;
		if(namedConditionContext instanceof MultiNamedConditionsContext){
			unit=visitMultiNamedConditions((MultiNamedConditionsContext)namedConditionContext, variableCategory);
		}else if(namedConditionContext instanceof SingleNamedConditionsContext){
			unit=visitSingleNamedConditions((SingleNamedConditionsContext)namedConditionContext, variableCategory);
		}else if(namedConditionContext instanceof ParenNamedConditionsContext){
			unit=visitParenNamedConditions((ParenNamedConditionsContext)namedConditionContext, variableCategory);
		}else{
			throw new RuleException("Unsupport context : +namedConditionContext+");
		}
		return unit;
	}
	
	private CriteriaUnit visitSingleNamedConditions(SingleNamedConditionsContext ctx,String variableCategory) {
		Criteria criteria=new Criteria();
		VariableLeftPart leftPart=new VariableLeftPart();
		Left left=new Left();
		left.setLeftPart(leftPart);
		left.setType(LeftType.NamedReference);
		criteria.setLeft(left);
		String variableName=ctx.property().getText();
		leftPart.setVariableLabel(variableName);
		leftPart.setVariableCategory(variableCategory);
		Op op=DSLUtils.parseOp(ctx.op());
		criteria.setOp(op);
		if(ctx.complexValue()==null){
			if(op.equals(Op.Equals)){
				criteria.setOp(Op.Null);
			}else if(op.equals(Op.NotEquals)){
				criteria.setOp(Op.NotNull);				
			}else{
				throw new RuleException("'null' value only support '==' or '!=' operator.");
			}
		}else{
			criteria.setValue(BuildUtils.buildValue(ctx.complexValue()));		
		}
		CriteriaUnit unit=new CriteriaUnit();
		unit.setCriteria(criteria);
		return unit;
	}
	private CriteriaUnit visitMultiNamedConditions(MultiNamedConditionsContext ctx,String variableCategory) {
		List<CriteriaUnit> nextUnits=new ArrayList<CriteriaUnit>();
		List<NamedConditionContext> namedConditions=ctx.namedCondition();
		if(namedConditions!=null){
			for(int i=0;i<namedConditions.size();i++){
				NamedConditionContext context=namedConditions.get(i);
				CriteriaUnit nextUnit=buildNamedCriteria(context, variableCategory);
				nextUnits.add(nextUnit);
				JoinContext joinContext=ctx.join(i);
				if(joinContext!=null){
					if(joinContext.AND()!=null){
						nextUnit.setJunctionType(JunctionType.and);
					}else{
						nextUnit.setJunctionType(JunctionType.or);				
					}
				}
			}
		}
		CriteriaUnit unit=new CriteriaUnit();
		unit.setNextUnits(nextUnits);
		return unit;
	}
	
	private CriteriaUnit visitParenNamedConditions(ParenNamedConditionsContext ctx,String variableCategory) {
		NamedConditionContext namedConditionContext=ctx.namedCondition();
		CriteriaUnit unit=buildNamedCriteria(namedConditionContext, variableCategory);
		return unit;
	}
}
