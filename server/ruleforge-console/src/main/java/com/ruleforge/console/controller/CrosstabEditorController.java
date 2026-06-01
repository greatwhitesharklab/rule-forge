package com.ruleforge.console.controller;

import com.ruleforge.console.servlet.CellContent;
import com.ruleforge.console.servlet.crosstab.*;
import com.ruleforge.exception.RuleException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;

@RestController
@RequestMapping("/${ruleforgeV2.root.path}/crosstabeditor")
@RequiredArgsConstructor
public class CrosstabEditorController {

    private static class CellSpan {
        int row;
        int col;
    }

    @PostMapping("/importExcel")
    public Map<String, Object> importExcel(@RequestParam("excel_file") MultipartFile file) throws Exception {
        Map<String, Object> result = new HashMap<>();
        try {
            if (file.isEmpty()) {
                throw new RuleException("请上传一个Excel文件！");
            }
            CrossData data;
            try (InputStream stream = file.getInputStream()) {
                data = parseExcel(stream);
            }
            result.put("fail", false);
            result.put("data", data);
        } catch (Exception ex) {
            String msg = buildErrorMsg(ex);
            result.put("fail", true);
            result.put("msg", msg);
        }
        return result;
    }

    private String buildErrorMsg(Exception ex) {
        Throwable e = buildCause(ex);
        if (e instanceof NullPointerException) {
            return "空指针错误！";
        } else {
            String msg = e.getMessage();
            return msg == null ? "服务端错误!" : msg;
        }
    }

    private Throwable buildCause(Throwable e) {
        return e.getCause() != null ? buildCause(e.getCause()) : e;
    }

    private CrossData parseExcel(InputStream stream) throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook(stream);
        if (wb.getNumberOfSheets() == 0) {
            wb.close();
            throw new RuleException("导入Excel不合法！");
        }
        List<CrossRow> rows = new ArrayList<>();
        List<CrossColumn> cols = new ArrayList<>();
        XSSFSheet sheet = wb.getSheetAt(0);
        CrossHeader header = buildHeader(sheet);
        XSSFRow firstRow = sheet.getRow(0);
        int totalColumn = firstRow.getLastCellNum();
        XSSFRow firstSpanRow = sheet.getRow(header.getRowSpan());

        for (int i = 0; i < totalColumn; ++i) {
            CrossColumn col = new CrossColumn();
            col.setNumber(i + 1);
            if (i < header.getColSpan()) {
                col.setType(Type.left);
                XSSFCell cell = firstSpanRow.getCell(i);
                XSSFComment cellComment = cell.getCellComment();
                if (cellComment != null) {
                    col.setContent(cellComment.getString().toString().toLowerCase().trim());
                }
            } else {
                col.setType(Type.top);
            }
            cols.add(col);
        }

        int totalRow = sheet.getLastRowNum();
        for (int i = 0; i <= totalRow; ++i) {
            XSSFRow row = sheet.getRow(i);
            CrossRow crossRow = new CrossRow();
            crossRow.setNumber(i + 1);
            if (i < header.getRowSpan()) {
                crossRow.setType(Type.top);
                XSSFCell firstCell = row.getCell(header.getColSpan());
                XSSFComment cellComment = firstCell.getCellComment();
                if (cellComment != null) {
                    crossRow.setContent(cellComment.getString().toString().toLowerCase().trim());
                }
            } else {
                crossRow.setType(Type.left);
            }
            rows.add(crossRow);
        }

        List<CellContent> cells = new ArrayList<>();
        for (int i = 0; i <= totalRow; ++i) {
            XSSFRow row = sheet.getRow(i);
            for (int j = 0; j < totalColumn; ++j) {
                if (i != 0 || j != 0) {
                    XSSFCell cell = row.getCell(j);
                    if (cell != null) {
                        CellSpan span = getCellSpan(i, j, sheet);
                        if (span != null) {
                            String cellData = getCellData(cell);
                            CellContent cc = new CellContent();
                            cc.setCol(j + 1);
                            cc.setRow(i + 1);
                            cc.setContent(cellData);
                            if (i < header.getRowSpan()) {
                                cc.setType("condition");
                                cc.setSpan(span.col);
                            }
                            if (j < header.getColSpan()) {
                                cc.setType("condition");
                                cc.setSpan(span.row);
                            }
                            cells.add(cc);
                        }
                    }
                }
            }
        }

        wb.close();
        CrossData data = new CrossData();
        data.setCells(cells);
        data.setColumns(cols);
        data.setRows(rows);
        data.setHeader(header);
        return data;
    }

    private CrossHeader buildHeader(XSSFSheet sheet) {
        CellSpan span = getCellSpan(0, 0, sheet);
        if (span == null) {
            throw new RuleException("导入的Excel不合法!");
        }
        CrossHeader header = new CrossHeader();
        header.setRowSpan(span.row);
        header.setColSpan(span.col);
        XSSFRow row = sheet.getRow(0);
        XSSFCell cell = row.getCell(0);
        header.setContent(getCellData(cell));
        return header;
    }

    private CellSpan getCellSpan(int row, int col, XSSFSheet sheet) {
        for (CellRangeAddress range : sheet.getMergedRegions()) {
            if (range.getFirstColumn() == col && range.getFirstRow() == row) {
                int rowSpan = range.getLastRow() - range.getFirstRow();
                if (rowSpan > 0) ++rowSpan;
                int colSpan = range.getLastColumn() - range.getFirstColumn();
                if (colSpan > 0) ++colSpan;
                CellSpan s = new CellSpan();
                s.row = rowSpan;
                s.col = colSpan;
                return s;
            }
            if (col >= range.getFirstColumn() && col <= range.getLastColumn()
                    && row >= range.getFirstRow() && row <= range.getLastRow()) {
                return null;
            }
        }
        CellSpan s = new CellSpan();
        s.row = 1;
        s.col = 1;
        return s;
    }

    private String getCellData(XSSFCell cell) {
        CellType type = cell.getCellType();
        switch (type) {
            case STRING:
                return cell.getStringCellValue();
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case NUMERIC:
                return String.valueOf(cell.getNumericCellValue());
            default:
                return null;
        }
    }
}
