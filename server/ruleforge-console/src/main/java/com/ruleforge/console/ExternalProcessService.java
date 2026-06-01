package com.ruleforge.console;

import java.util.Date;

public interface ExternalProcessService {

    void syncExec(String fullPackageId, String env, String username, Integer proportion, Date start, Date end);

    String start(String project, String title, String nowVersion, String version,
                 String explain, String remark, String fileName, String filePath,
                 String passRateEffect, Double passRateRange,
                 String badDebtRateEffect, Double badDebtRateRange) throws Exception;

    String testStart(String title, String project, String fileName, Date startTime, Date endTime,
                     String version, Integer testRate, String remark, String explain) throws Exception;
}
