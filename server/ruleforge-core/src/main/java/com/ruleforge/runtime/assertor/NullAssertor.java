package com.ruleforge.runtime.assertor;

import org.apache.commons.lang.StringUtils;

import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rule.Op;

/**
 * @author Jacky.gao
 * @since 2015年1月6日
 */
public class NullAssertor implements Assertor {

	public boolean eval(Object left, Object right,Datatype datatype) {
		if(left==null){
			return true;
		}else{
			return StringUtils.isBlank(left.toString());
		}
	}

	public boolean support(Op op) {
		return op.equals(Op.Null);
	}
}
