package com.ruleforge.runtime.agenda;

public interface AgendaFilter {
    boolean accept(Activation activation);
}
