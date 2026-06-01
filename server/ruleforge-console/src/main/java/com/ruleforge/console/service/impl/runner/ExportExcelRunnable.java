package com.ruleforge.console.service.impl.runner;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruleforge.builder.KnowledgeBase;
import com.ruleforge.console.repository.ExternalRepository;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.library.variable.VariableCategory;
import com.ruleforge.console.model.BaseProcessDto;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;

import java.awt.*;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;

@Slf4j
@AllArgsConstructor

public class ExportExcelRunnable implements Runnable {

    private final ExternalRepository externalRepository;
    private final KnowledgeBase knowledgeBase;
    private final String project;
    private final String packageId;
    private final Date startDate;
    private final Date endDate;
    private final Integer limit;
    private final BlockingQueue<BaseProcessDto<SXSSFWorkbook>> exportExcelDataDtoBlockingQueue;

    @Override
    public void run() {
        try {
            List<VariableCategory> variableCategories = this.knowledgeBase.getResourceLibrary().getVariableCategories();

            // 获取历史数据
            JSONArray data;
            if (this.limit == null) {
                data = this.externalRepository.findDataByDate(this.startDate, this.endDate, this.project, this.packageId);
            } else {
                data = this.externalRepository.findDataByLimit(this.limit, this.project, this.packageId);
            }

            SXSSFWorkbook wb = new SXSSFWorkbook();
            XSSFCellStyle style = (XSSFCellStyle) wb.createCellStyle();
            Color c = new Color(147, 208, 15);
            XSSFColor xssfColor = new XSSFColor(new byte[]{(byte) 147, (byte) 208, (byte) 15}, null);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setFillForegroundColor(xssfColor);

            int totalVc = variableCategories.size();
            double completedVc = 0;
            for (VariableCategory vc : variableCategories) {
                buildSheet(wb, vc, style, data);
                completedVc++;
                this.exportExcelDataDtoBlockingQueue.add(new BaseProcessDto<>(completedVc / totalVc));
            }

            this.exportExcelDataDtoBlockingQueue.add(new BaseProcessDto<>(wb));
        } catch (Exception e) {
            log.error("ExportExcelRunnable run error", e);
        }
    }

    private void buildSheet(SXSSFWorkbook wb, VariableCategory vc, XSSFCellStyle style, JSONArray data) {
        String name = vc.getName();
        Sheet sheet = wb.createSheet(name);
        int rowNum = 0;

        // 表头
        Row row0 = sheet.createRow(rowNum);
        List<Variable> variables = vc.getVariables();
        for (int i = 0; i < variables.size(); i++) {
            sheet.setColumnWidth(i, 4000);
            Cell cell = row0.createCell(i);
            Variable var = variables.get(i);
            cell.setCellValue(var.getLabel());
            cell.setCellStyle(style);
        }

        // 历史数据
        if (data != null) {
            for (Object obj : data.toArray()) {
                rowNum++;

                JSONObject jobj = (JSONObject) obj;
                Object dataSource = jobj.get(vc.getClazz());
                if (dataSource == null) {
                    continue;
                }

                JSONObject dataSourceJobj = (JSONObject) dataSource;
                Row row = sheet.createRow(rowNum);
                for (int i = 0; i < variables.size(); i++) {
                    Cell cell = row.createCell(i);
                    Variable var = variables.get(i);

                    if (dataSourceJobj.get(var.getName()) == null) {
                        continue;
                    }
                    switch (var.getType()) {
                        case Integer:
                            cell.setCellValue(dataSourceJobj.getInteger(var.getName()));
                            break;
                        case Double:
                            cell.setCellValue(dataSourceJobj.getDouble(var.getName()));
                            break;
                        case Long:
                            cell.setCellValue(dataSourceJobj.getLong(var.getName()));
                            break;
                        case BigDecimal:
                            cell.setCellValue(dataSourceJobj.getBigDecimal(var.getName()).doubleValue());
                            break;
                        case String:
                        default:
                            cell.setCellValue(dataSourceJobj.getString(var.getName()));
                    }
                }
            }
        }
    }

}
