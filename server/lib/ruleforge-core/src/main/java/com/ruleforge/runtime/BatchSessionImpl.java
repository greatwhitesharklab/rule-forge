package com.ruleforge.runtime;

import com.ruleforge.exception.RuleException;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author Jacky.gao
 * @since 2015年9月29日
 */
@Slf4j
public class BatchSessionImpl implements BatchSession {
    private ExecutorService executorService;
    private int batchSize;
    private List<Business> businessList = new ArrayList<Business>();
    private KnowledgePackage knowledgePackage;
    private KnowledgePackage[] knowledgePackages;

    public BatchSessionImpl(KnowledgePackage knowledgePackage, int threadSize, int batchSize) {
        this.executorService = Executors.newFixedThreadPool(threadSize);
        this.knowledgePackage = knowledgePackage;
        this.batchSize = batchSize;
    }

    public BatchSessionImpl(KnowledgePackage[] knowledgePackages, int threadSize, int batchSize) {
        this.executorService = Executors.newFixedThreadPool(threadSize);
        this.knowledgePackages = knowledgePackages;
        this.batchSize = batchSize;
    }

    @Override
    public void addBusiness(Business business) {
        if (businessList != null) {
            if (businessList.size() >= batchSize) {
                doBusinesses();
                businessList = new ArrayList<Business>();
            }
        } else {
            businessList = new ArrayList<Business>();
        }
        businessList.add(business);
    }

    private void doBusinesses() {
        BatchThread thread = null;
        if (knowledgePackage != null) {
            thread = new BatchThread(knowledgePackage, businessList);
        } else if (knowledgePackages != null) {
            thread = new BatchThread(knowledgePackages, businessList);
        } else {
            throw new RuleException("KnowledgePackage can not be null.");
        }
        executorService.execute(thread);
        businessList = null;
    }

    @Override
    public void waitForCompletion() {
        if (businessList != null && !businessList.isEmpty()) {
            doBusinesses();
        }
        executorService.shutdown();
        try {
            while (!executorService.awaitTermination(300, TimeUnit.MILLISECONDS)) {
            }
        } catch (InterruptedException ex) {
            log.error("waitForCompletion", ex);
            throw new RuleException(ex);
        }
    }
}
