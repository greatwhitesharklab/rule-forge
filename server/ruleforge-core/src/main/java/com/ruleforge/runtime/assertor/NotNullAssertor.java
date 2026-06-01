package com.ruleforge.runtime.assertor;

import org.apache.commons.lang.StringUtils;

import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rule.Op;

/**
 * @author Jacky.gao
 * @since 2015年1月6日
 */
public class NotNullAssertor implements Assertor {

	public boolean eval(Object left, Object right,Datatype datatype) {
		if(left!=null && StringUtils.isNotBlank(left.toString())){
			return true;
		}else{
			return false;
		}
	}

	public boolean support(Op op) {
		return op.equals(Op.NotNull);
	}
}
