package com.ruleforge.runtime;

import com.ruleforge.runtime.event.KnowledgeEvent;
import com.ruleforge.runtime.event.KnowledgeEventListener;

import java.util.List;

public interface KnowledgeEventManager {
    void addEventListener(KnowledgeEventListener var1);

    List<KnowledgeEventListener> getKnowledgeEventListeners();

    boolean removeEventListener(KnowledgeEventListener var1);

    void fireEvent(KnowledgeEvent var1);
}
