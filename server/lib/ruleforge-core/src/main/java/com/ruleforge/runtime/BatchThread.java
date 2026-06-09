package com.ruleforge.runtime;

import java.util.List;

/**
 * @author Jacky.gao
 * @since 2015年9月29日
 */
public class BatchThread implements Runnable {
    private List<Business> businesses;
    private KnowledgeSession session;

    public BatchThread(KnowledgePackage knowledgePackage, List<Business> businesses) {
        session = KnowledgeSessionFactory.newKnowledgeSession(knowledgePackage);
        this.businesses = businesses;
    }

    public BatchThread(KnowledgePackage[] knowledgePackages, List<Business> businesses) {
        session = KnowledgeSessionFactory.newKnowledgeSession(knowledgePackages);
        this.businesses = businesses;
    }

    @Override
    public void run() {
        Thread thread = Thread.currentThread();
        String oldThreadName = thread.getName();
        thread.setName("ruleforge-" + oldThreadName);
        try {
            for (Business business : businesses) {
                business.execute(session);
            }
        } finally {
            thread.setName(oldThreadName);
        }
    }
}
