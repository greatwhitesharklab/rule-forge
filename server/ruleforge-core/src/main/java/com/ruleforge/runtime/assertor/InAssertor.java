package com.ruleforge.runtime.assertor;

import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rule.Op;

import java.util.Collection;

public class InAssertor implements Assertor {
    public boolean eval(Object left, Object right, Datatype datatype) {
        if (left == null || right == null) {
            return false;
        }
        if (right instanceof Collection) {
            Collection<?> coll = (Collection<?>) right;
            if (left instanceof Collection) {
                Collection<?> leftColl = (Collection<?>) left;
                boolean match = false;
                for (Object leftObj : leftColl) {
                    for (Object obj : coll) {
                        if (obj.toString().equals(leftObj.toString())) {
                            match = true;
                            break;
                        } else {
                            match = false;
                        }
                    }
                }
                return match;
            } else {
                String[] leftStrings = left.toString().split(",");
                boolean match = false;
                for (String str : leftStrings) {
                    for (Object obj : coll) {
                        if (obj.toString().equals(str)) {
                            match = true;
                            break;
                        } else {
                            match = false;
                        }
                    }
                }
                return match;
            }
        } else if (right instanceof String) {
            String str = (String) right;
            String[] array = str.split(",");
            if (left instanceof Collection) {
                Collection<?> leftColl = (Collection<?>) left;
                boolean match = false;
                for (Object leftObj : leftColl) {
                    for (String rightStr : array) {
                        if (leftObj.toString().equals(rightStr)) {
                            match = true;
                            break;
                        } else {
                            match = false;
                        }
                    }
                }
                return match;
            } else {
                String[] leftStrings = left.toString().split(",");
                boolean match = false;
                for (String leftStr : leftStrings) {
                    for (String righStr : array) {
                        if (righStr.equals(leftStr)) {
                            match = true;
                            break;
                        } else {
                            match = false;
                        }
                    }
                }
                return match;
            }
        }
        return false;
    }

    public boolean support(Op op) {
        return op.equals(Op.In);
    }
}
