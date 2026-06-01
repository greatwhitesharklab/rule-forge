package com.ruleforge.runtime.assertor;

import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rule.Op;

/**
 * @author Jacky.gao
 * @since 2015年1月6日
 */
public class NotEndWithAssertor implements Assertor {

	public boolean eval(Object left, Object right,Datatype datatype) {
		if(left==null || right==null){
			return false;
		}
		if(!(left instanceof String) || !(right instanceof String)){
			return false;
		}
		String leftStr=(String)left;
		String rightStr=(String)right;
		if(!leftStr.endsWith(rightStr)){
			return true;
		}
		return false;
	}

	public boolean support(Op op) {
		return op.equals(Op.NotEndWith);
	}
}
