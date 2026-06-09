package com.ruleforge.runtime.builtinaction;

import java.math.BigDecimal;

import com.ruleforge.Utils;
import com.ruleforge.model.library.action.annotation.ActionBean;
import com.ruleforge.model.library.action.annotation.ActionMethod;
import com.ruleforge.model.library.action.annotation.ActionMethodParameter;
/**
 * @author Jacky.gao
 * @since 2015年11月27日
 */
@ActionBean(name="数学函数")
public class MathAction {
	@ActionMethod(name="求绝对值")
	@ActionMethodParameter(names={"数字"})
	public Number abs(Object obj){
		BigDecimal v1=Utils.toBigDecimal(obj);
		return Math.abs(v1.doubleValue());
	}
	@ActionMethod(name="求最大值")
	@ActionMethodParameter(names={"数字1","数字2"})
	public Number max(Object obj,Object obj1){
		BigDecimal v1=Utils.toBigDecimal(obj);
		BigDecimal v2=Utils.toBigDecimal(obj1);
		return Math.max(v1.doubleValue(), v2.doubleValue());
	}
	@ActionMethod(name="求最小值")
	@ActionMethodParameter(names={"数字1","数字2"})
	public Number min(Object obj,Object obj1){
		BigDecimal v1=Utils.toBigDecimal(obj);
		BigDecimal v2=Utils.toBigDecimal(obj1);
		return Math.min(v1.doubleValue(), v2.doubleValue());
	}
	
	@ActionMethod(name="求正弦")
	@ActionMethodParameter(names={"数字"})
	public Number in(Object obj){
		BigDecimal v1=Utils.toBigDecimal(obj);
		return Math.sin(v1.doubleValue());
	}
	@ActionMethod(name="求余弦")
	@ActionMethodParameter(names={"数字"})
	public Number cos(Object obj){
		BigDecimal v1=Utils.toBigDecimal(obj);
		return Math.cos(v1.doubleValue());
	}
	@ActionMethod(name="求正切")
	@ActionMethodParameter(names={"数字"})
	public Number tan(Object obj){
		BigDecimal v1=Utils.toBigDecimal(obj);
		return Math.tan(v1.doubleValue());
	}
	@ActionMethod(name="求余切")
	@ActionMethodParameter(names={"数字"})
	public Number cot(Object obj){
		BigDecimal v1=Utils.toBigDecimal(obj);
		return 1/Math.tan(v1.doubleValue());
	}
	@ActionMethod(name="求e为底的对数")
	@ActionMethodParameter(names={"数字"})
	public Number log(Object obj){
		BigDecimal v1=Utils.toBigDecimal(obj);
		return Math.log(v1.doubleValue());
	}
	@ActionMethod(name="求10为底的对数")
	@ActionMethodParameter(names={"数字"})
	public Number log10(Object obj){
		BigDecimal v1=Utils.toBigDecimal(obj);
		return Math.log10(v1.doubleValue());
	}
	
	@ActionMethod(name="四舍五入")
	@ActionMethodParameter(names={"数字"})
	public Number round(Object obj){
		BigDecimal v1=Utils.toBigDecimal(obj);
		return Math.round(v1.doubleValue());
	}
}
