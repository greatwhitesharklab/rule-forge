package com.ruleforge.runtime.agenda;

import java.util.ArrayList;
import java.util.List;

import com.ruleforge.action.ActionValue;
import com.ruleforge.model.rule.RuleInfo;
import com.ruleforge.runtime.rete.Context;

/**
 * @author Jacky.gao
 * @since 2015年1月2日
 */
public class AgendaGroup extends RuleGroup{
	private boolean focus;
	private boolean executed;
	public AgendaGroup(String name,List<RuleInfo> executedRules) {
		super(name, executedRules);
	}
	@Override
	public List<RuleInfo> execute(Context context,AgendaFilter filter, int max,List<ActionValue> actionValues){
		executed=true;
		List<RuleInfo> ruleInfos=new ArrayList<RuleInfo>();
		for(int i=0;i<max;i++){
			Activation activation=fetchNextExecutableActivation(activations);
			if(activation==null){
				return ruleInfos;
			}
			activations.remove(activation);
			ActivationImpl ac=(ActivationImpl)activation;
			ac.setProcessed(true);
			boolean autoFocus=false;
			if(activation.getRule().getAutoFocus()!=null){
				autoFocus=activation.getRule().getAutoFocus();
			}
			RuleInfo ruleInfo=null;
			if(focus){
				ruleInfo=activation.execute(context, executedRules,actionValues);
			}else if(autoFocus){
				ruleInfo=activation.execute(context, executedRules,actionValues);
			}
			if(ruleInfo!=null){
				ruleInfos.add(ruleInfo);
			}
			if(ruleInfos.size()>=max){
				break;
			}
		}
		return ruleInfos;
	}

	public boolean isExecuted() {
		return executed;
	}
	
	public boolean isFocus() {
		return focus;
	}

	public void setFocus(boolean focus) {
		this.focus = focus;
	}
}
