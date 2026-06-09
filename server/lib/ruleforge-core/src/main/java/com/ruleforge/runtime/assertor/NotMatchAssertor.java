package com.ruleforge.runtime.assertor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rule.Op;

/**
 * @author Jacky.gao
 * @since 2015年1月6日
 */
public class NotMatchAssertor implements Assertor {
	public boolean eval(Object left, Object right,Datatype datatype) {
		if(left==null || right==null){
			return false;
		}
		Pattern pattern=Pattern.compile(right.toString());
		Matcher matcher=pattern.matcher(left.toString());
		return !matcher.matches();
	}

	public boolean support(Op op) {
		return op.equals(Op.NotMatch);
	}

}
