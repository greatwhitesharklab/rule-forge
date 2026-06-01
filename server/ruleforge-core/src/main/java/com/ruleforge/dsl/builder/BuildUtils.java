package com.ruleforge.dsl.builder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.ruleforge.exception.RuleException;
import com.ruleforge.Utils;
import com.ruleforge.dsl.RuleParserParser.ActionParametersContext;
import com.ruleforge.dsl.RuleParserParser.BeanMethodContext;
import com.ruleforge.dsl.RuleParserParser.CommonFunctionContext;
import com.ruleforge.dsl.RuleParserParser.ComplexValueContext;
import com.ruleforge.dsl.RuleParserParser.ConstantContext;
import com.ruleforge.dsl.RuleParserParser.MethodInvokeContext;
import com.ruleforge.dsl.RuleParserParser.NamedVariableContext;
import com.ruleforge.dsl.RuleParserParser.ParameterContext;
import com.ruleforge.dsl.RuleParserParser.PropertyContext;
import com.ruleforge.dsl.RuleParserParser.ValueContext;
import com.ruleforge.dsl.RuleParserParser.VariableCategoryContext;
import com.ruleforge.dsl.RuleParserParser.VariableContext;
import com.ruleforge.model.function.FunctionDescriptor;
import com.ruleforge.model.rule.AbstractValue;
import com.ruleforge.model.rule.ArithmeticType;
import com.ruleforge.model.rule.CommonFunctionValue;
import com.ruleforge.model.rule.ComplexArithmetic;
import com.ruleforge.model.rule.ConstantValue;
import com.ruleforge.model.rule.MethodValue;
import com.ruleforge.model.rule.NamedReferenceValue;
import com.ruleforge.model.rule.Parameter;
import com.ruleforge.model.rule.ParameterValue;
import com.ruleforge.model.rule.ParenValue;
import com.ruleforge.model.rule.SimpleValue;
import com.ruleforge.model.rule.Value;
import com.ruleforge.model.rule.VariableCategoryValue;
import com.ruleforge.model.rule.VariableValue;
import com.ruleforge.model.rule.lhs.CommonFunctionParameter;

/**
 * @author Jacky.gao
 * @since 2016年6月1日
 */
public class BuildUtils {
	public static AbstractValue buildValue(ComplexValueContext context){
		AbstractValue value=null;
		if(context.leftParen()!=null){
			ParenValue pv=new ParenValue();
			List<ComplexValueContext> values=context.complexValue();
			Value v=buildValue(values.get(0));
			pv.setValue(v);
			value=pv;
		}else if(context.value()!=null){
			value=buildSimpleValue(context.value());
		}else if(context.variable()!=null){
			value=buildVariableValue(context.variable());
		}else if(context.constant()!=null){
			value=buildConstantValue(context.constant());
		}else if(context.variableCategory()!=null){
			VariableCategoryContext vcc=context.variableCategory();
			String name=vcc.Identifier().getText();
			value=new VariableCategoryValue(name);
		}else if(context.parameter()!=null){
			ParameterContext parameterContext=context.parameter();
			ParameterValue parameterValue=new ParameterValue();
			parameterValue.setVariableLabel(parameterContext.Identifier().getText());
			value=parameterValue;
		}else if(context.namedVariable()!=null){
			NamedVariableContext namedVariableContext=context.namedVariable();
			String refName=namedVariableContext.namedVariableCategory().getText();
			String property=namedVariableContext.property().getText();
			NamedReferenceValue refValue=new NamedReferenceValue();
			refValue.setReferenceName(refName);
			refValue.setPropertyLabel(property);
			value=refValue;
		}else if(context.methodInvoke()!=null){
			MethodInvokeContext actionContext=(MethodInvokeContext)context.methodInvoke();
			MethodValue mv=new MethodValue();
			BeanMethodContext beanMethodContext=actionContext.beanMethod();
			String beanLabel=beanMethodContext.Identifier(0).getText();
			String methodLabel=beanMethodContext.Identifier(1).getText();
			mv.setBeanLabel(beanLabel);
			mv.setMethodLabel(methodLabel);
			ActionParametersContext actionParametersContext=actionContext.actionParameters();
			if(actionParametersContext!=null && actionParametersContext.complexValue()!=null){
				List<ComplexValueContext> values=actionParametersContext.complexValue();
				List<Parameter> parameters=new ArrayList<Parameter>();
				for(ComplexValueContext cvx:values){
					Parameter parameter=new Parameter();
					parameter.setValue(buildValue(cvx));
					parameters.add(parameter);
				}
				mv.setParameters(parameters);
			}
			value=mv;
		}else if(context.commonFunction()!=null){
			CommonFunctionContext commonFunctionContext=context.commonFunction();
			Collection<FunctionDescriptor> functionDescriptors=Utils.getApplicationContext().getBeansOfType(FunctionDescriptor.class).values();
			CommonFunctionValue functionValue=new CommonFunctionValue();
			String nameorlabel=commonFunctionContext.Identifier().getText();
			for(FunctionDescriptor fun:functionDescriptors){
				if(nameorlabel.equals(fun.getName())){
					functionValue.setName(fun.getName());
					functionValue.setLabel(fun.getLabel());
					break;
				}else if(nameorlabel.equals(fun.getLabel())){
					functionValue.setName(fun.getName());;
					functionValue.setLabel(fun.getLabel());
					break;
				}
			}
			if(functionValue.getName()==null){
				throw new RuleException("Function["+nameorlabel+"] not exist.");
			}
			ComplexValueContext complexValue=commonFunctionContext.complexValue();
			CommonFunctionParameter param=new CommonFunctionParameter();
			param.setObjectParameter(buildValue(complexValue));
			PropertyContext propertyContext=commonFunctionContext.property();
			if(propertyContext!=null){
				param.setProperty(propertyContext.getText());
			}
			functionValue.setParameter(param);
			value=functionValue;
		}else if(context.complexValue()!=null){
			List<ComplexValueContext> values=context.complexValue();
			value=buildValue(values.get(0));
		}
		List<TerminalNode> arithList=context.ARITH();
		if(arithList!=null && arithList.size()>0){
			TerminalNode arithNode=arithList.get(0);
			ComplexArithmetic arith=new ComplexArithmetic();
			arith.setType(ArithmeticType.parse(arithNode.getText()));
			ParseTree nextContext=context.getChild(2);
			arith.setValue(buildValue((ComplexValueContext)nextContext));
			value.setArithmetic(arith);			
		}
		return value;
	}
	private static ConstantValue buildConstantValue(ConstantContext context){
		ConstantValue value=new ConstantValue();
		value.setConstantCategory(context.constantCategory().Identifier().getText());
		value.setConstantLabel(context.property().getText());
		return value;
	}
	private static VariableValue buildVariableValue(VariableContext context){
		VariableValue value=new VariableValue();
		value.setVariableCategory(context.variableCategory().getText());
		value.setVariableLabel(context.property().getText());
		return value;
	}
	private static SimpleValue buildSimpleValue(ValueContext context){
		SimpleValue value=new SimpleValue();
		if(context.STRING()!=null){
			value.setContent(getSTRINGContent(context.STRING()));
		}else if(context.Boolean()!=null){
			value.setContent(context.Boolean().getText());
		}else if(context.NUMBER()!=null){
			value.setContent(context.NUMBER().getText());
		}
		return value;
	}
	
	public static String getSTRINGContent(TerminalNode node){
		String text=node.getText();
		return text.substring(1,text.length()-1);
	}
}
