package com.ruleforge.runtime.assertor;

import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rule.Op;

/**
 * @author Jacky.gao
 * @since 2015年1月6日
 */
public interface Assertor {
	boolean eval(Object left,Object right,Datatype datatype);
	boolean support(Op op);
}
