package com.ruleforge.runtime;

import com.ruleforge.runtime.event.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @author fred
 * 2018-11-05 5:43 PM
 */
public class KnowledgeEventManagerImpl implements KnowledgeEventManager {
    private List<KnowledgeEventListener> listeners = new ArrayList<>();

    public void addEventListener(KnowledgeEventListener listener) {
        this.listeners.add(listener);
    }

    public List<KnowledgeEventListener> getKnowledgeEventListeners() {
        return this.listeners;
    }

    public boolean removeEventListener(KnowledgeEventListener listener) {
        return this.listeners.remove(listener);
    }

    public void fireEvent(KnowledgeEvent event) {
        if (event instanceof ActivationEvent) {
            for (KnowledgeEventListener listener : this.listeners) {
                if (listener instanceof AgendaEventListener) {
                    AgendaEventListener lis = (AgendaEventListener) listener;
                    if (event instanceof ActivationCancelledEvent) {
                        ActivationCancelledEvent e = (ActivationCancelledEvent) event;
                        lis.activationCancelled(e);
                    } else if (event instanceof ActivationCreatedEvent) {
                        ActivationCreatedEvent e = (ActivationCreatedEvent) event;
                        lis.activationCreated(e);
                    } else if (event instanceof ActivationBeforeFiredEvent) {
                        ActivationBeforeFiredEvent e = (ActivationBeforeFiredEvent) event;
                        lis.beforeActivationFired(e);
                    } else if (event instanceof ActivationAfterFiredEvent) {
                        ActivationAfterFiredEvent e = (ActivationAfterFiredEvent) event;
                        lis.afterActivationFired(e);
                    }
                }
            }
        }
    }
}