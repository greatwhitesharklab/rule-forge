package com.ruleforge.console.service;

import com.ruleforge.console.model.BaseProcessDto;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.util.Date;
import java.util.concurrent.BlockingQueue;

/**
 * @author Fred
 * @since 2025/8/26 10:28
 */
@Deprecated
public interface PackageService {

    BlockingQueue<BaseProcessDto<SXSSFWorkbook>> exportExcelData(String project, String packageId, Date startDate, Date endDate, Integer limit);
}
