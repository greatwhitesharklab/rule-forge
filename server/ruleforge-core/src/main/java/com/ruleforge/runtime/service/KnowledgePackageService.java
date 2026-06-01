package com.ruleforge.runtime.service;

import com.ruleforge.exception.RuleException;
import com.ruleforge.runtime.KnowledgePackage;

import java.io.IOException;

public interface KnowledgePackageService {
    String BEAN_ID = "ruleforge.knowledgePackageService";

    KnowledgePackage buildKnowledgePackage(String packageInfo) throws RuleException;

    KnowledgePackage buildKnowledgePackage(String packageInfo, Boolean latest) throws RuleException;

    boolean isKnowledgePackageNeedUpdate(String packageInfo) throws IOException;
}
