package com.ruleforge.runtime;

import com.ruleforge.runtime.event.KnowledgeEvent;
import com.ruleforge.runtime.event.KnowledgeEventListener;

import java.util.List;

public interface EventManager {
    void addEventListener(KnowledgeEventListener listener);

    boolean removeEventListener(KnowledgeEventListener listener);

    void fireEvent(KnowledgeEvent event);

    List<KnowledgeEventListener> getKnowledgeEventListeners();
}
