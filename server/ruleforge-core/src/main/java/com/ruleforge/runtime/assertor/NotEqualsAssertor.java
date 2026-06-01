package com.ruleforge.runtime.assertor;

import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rule.Op;

/**
 * @author Jacky.gao
 * @since 2015年1月6日
 */
public class NotEqualsAssertor extends EqualsAssertor {
	public boolean eval(Object left, Object right,Datatype datatype) {
		if(left==null && right!=null){
			return true;
		}else if(left!=null && right==null){
			return true;
		}else if(left==null && right==null){
			return false;
		}
		return !super.eval(left, right, datatype);
	}

	public boolean support(Op op) {
		return op.equals(Op.NotEquals);
	}
}
