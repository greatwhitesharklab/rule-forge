package com.ruleforge.runtime.assertor;

import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rule.Op;

public class NotInAssertor extends InAssertor {
    @Override
    public boolean eval(Object left, Object right, Datatype datatype) {
        return !super.eval(left, right, datatype);
    }

    @Override
    public boolean support(Op op) {
        return op.equals(Op.NotIn);
    }
}
