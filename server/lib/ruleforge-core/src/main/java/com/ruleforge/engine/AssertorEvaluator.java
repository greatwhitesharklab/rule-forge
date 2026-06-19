package com.ruleforge.engine;
import com.ruleforge.runtime.assertor.Assertor;
import java.util.Collection;

import com.ruleforge.exception.RuleException;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rule.Op;
import com.ruleforge.plugin.EnginePluginRegistry;

import java.util.Collection;

/**
 * @author Jacky.gao
 * 2015年1月6日
 */
public class AssertorEvaluator {
    public static final String BEAN_ID = "ruleforge.assertorEvaluator";
    private Collection<Assertor> assertors;

    public boolean evaluate(Object left, Object right, Datatype datatype, Op op) {
        Assertor targetAssertor = null;
        for (Assertor assertor : assertors) {
            if (assertor.support(op)) {
                targetAssertor = assertor;
                break;
            }
        }
        if (targetAssertor == null) {
            throw new RuleException("Unsupport op:" + op);
        }
        return targetAssertor.eval(left, right, datatype);
    }

    public void setPluginRegistry(EnginePluginRegistry pluginRegistry) {
        this.assertors = pluginRegistry.getAssertors();
    }
}
