package com.ruleforge.runtime.builtinaction;

import com.ruleforge.action.ActionId;
import com.ruleforge.model.library.action.annotation.ActionBean;
import com.ruleforge.model.library.action.annotation.ActionMethod;
import com.ruleforge.model.library.action.annotation.ActionMethodParameter;

/**
 * @author Jacky.gao
 * @since 2016年9月30日
 */
@ActionBean(name="循环操作")
public class LoopAction {
	public static final String BREAK_LOOP_ACTION_ID="_loop_break__";
	@ActionMethod(name="中断循环")
	@ActionMethodParameter(names={})
	@ActionId(BREAK_LOOP_ACTION_ID)
	public String breakLoop(){
		return "break";
	}
}
