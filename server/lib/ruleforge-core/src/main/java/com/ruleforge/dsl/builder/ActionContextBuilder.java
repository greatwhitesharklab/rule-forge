package com.ruleforge.dsl.builder;

import java.util.Collection;

import org.antlr.v4.runtime.ParserRuleContext;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.ruleforge.exception.RuleException;
import com.ruleforge.action.Action;
import com.ruleforge.action.ConsolePrintAction;
import com.ruleforge.action.ExecuteCommonFunctionAction;
import com.ruleforge.action.ExecuteMethodAction;
import com.ruleforge.action.VariableAssignAction;
import com.ruleforge.dsl.RuleParserParser.ActionContext;
import com.ruleforge.dsl.RuleParserParser.ActionParametersContext;
import com.ruleforge.dsl.RuleParserParser.AssignActionContext;
import com.ruleforge.dsl.RuleParserParser.BeanMethodContext;
import com.ruleforge.dsl.RuleParserParser.CommonFunctionContext;
import com.ruleforge.dsl.RuleParserParser.ComplexValueContext;
import com.ruleforge.dsl.RuleParserParser.MethodInvokeContext;
import com.ruleforge.dsl.RuleParserParser.NamedVariableContext;
import com.ruleforge.dsl.RuleParserParser.OutActionContext;
import com.ruleforge.dsl.RuleParserParser.ParameterContext;
import com.ruleforge.dsl.RuleParserParser.PropertyContext;
import com.ruleforge.model.function.FunctionDescriptor;
import com.ruleforge.model.library.variable.VariableCategory;
import com.ruleforge.model.rule.Parameter;
import com.ruleforge.model.rule.Value;
import com.ruleforge.model.rule.lhs.CommonFunctionParameter;
import com.ruleforge.model.rule.lhs.LeftType;

/**
 * @author Jacky.gao
 * @since 2015年2月15日
 */
public class ActionContextBuilder extends AbstractContextBuilder implements ApplicationContextAware{
	private Collection<FunctionDescriptor> functionDescriptors;
	@Override
	public Action build(ParserRuleContext context) {
		ActionContext ctx=(ActionContext)context;
		if(ctx.outAction()!=null){
			return buildConsolePrintAction(ctx.outAction());
		}else if(ctx.assignAction()!=null){
			return buildVariableAssignAction(ctx.assignAction());
		}else if(ctx.methodInvoke()!=null){
			return buildExecuteMethodAction(ctx.methodInvoke());
		}else if(ctx.commonFunction()!=null){
			return buildExecuteCommonFunctionAction(ctx.commonFunction());
		}
		return null;
	}
	
	private ExecuteCommonFunctionAction buildExecuteCommonFunctionAction(CommonFunctionContext context){
		ExecuteCommonFunctionAction action=new ExecuteCommonFunctionAction();
		String nameorlabel=context.Identifier().getText();
		for(FunctionDescriptor fun:functionDescriptors){
			if(nameorlabel.equals(fun.getName())){
				action.setName(fun.getName());
				action.setLabel(fun.getLabel());
				break;
			}else if(nameorlabel.equals(fun.getLabel())){
				action.setName(fun.getName());
				action.setLabel(fun.getLabel());
				break;
			}
		}
		if(action.getName()==null){
			throw new RuleException("Function["+nameorlabel+"] not exist.");
		}
		ComplexValueContext value=context.complexValue();
		CommonFunctionParameter param=new CommonFunctionParameter();
		param.setObjectParameter(BuildUtils.buildValue(value));
		PropertyContext propertyContext=context.property();
		if(propertyContext!=null){
			param.setProperty(propertyContext.getText());
		}
		action.setParameter(param);
		return action;
	}
		
	private ExecuteMethodAction buildExecuteMethodAction(MethodInvokeContext context){
		ExecuteMethodAction action=new ExecuteMethodAction();
		BeanMethodContext methodContext=context.beanMethod();
		action.setBeanLabel(methodContext.getChild(0).getText());
		action.setMethodLabel(methodContext.getChild(2).getText());
		ActionParametersContext parametersContext=context.actionParameters();
		if(parametersContext!=null){
			for(ComplexValueContext ctx:parametersContext.complexValue()){
				Parameter parameter=new Parameter();
				parameter.setValue(BuildUtils.buildValue(ctx));
				action.addParameter(parameter);
			}
		}
		return action;
	}
	
	private VariableAssignAction buildVariableAssignAction(AssignActionContext context){
		VariableAssignAction action=new VariableAssignAction();
		ParameterContext parameterContext=context.parameter();
		NamedVariableContext namedVariableContext=context.namedVariable();
		if(namedVariableContext!=null){
			action.setReferenceName(namedVariableContext.namedVariableCategory().getText());
			action.setVariableLabel(namedVariableContext.property().getText());
			action.setType(LeftType.NamedReference);
		}else if(parameterContext==null){
			action.setVariableCategory(context.variable().variableCategory().getText());
			action.setVariableLabel(context.variable().property().getText());			
		}else{
			action.setVariableCategory(VariableCategory.PARAM_CATEGORY);
			action.setVariableLabel(parameterContext.Identifier().getText());
		}
		action.setValue(BuildUtils.buildValue(context.complexValue()));
		return action;
	}
	
	private ConsolePrintAction buildConsolePrintAction(OutActionContext context){
		ConsolePrintAction action=new ConsolePrintAction();
		Value value=BuildUtils.buildValue(context.complexValue());
		action.setValue(value);
		return action;
	}
	@Override
	public void setApplicationContext(ApplicationContext applicationContext)
			throws BeansException {
		functionDescriptors=applicationContext.getBeansOfType(FunctionDescriptor.class).values();
	}
	@Override
	public boolean support(ParserRuleContext context) {
		return context instanceof ActionContext;
	}
}
