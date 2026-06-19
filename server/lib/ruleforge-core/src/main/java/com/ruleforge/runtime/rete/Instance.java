package com.ruleforge.runtime.rete;
import com.ruleforge.engine.EvaluationContext;

import java.util.Collection;

public interface Instance {
    Collection<FactTracker> enter(EvaluationContext context, Object obj, FactTracker tracker);
}
