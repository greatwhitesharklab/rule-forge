package com.ruleforge.console.util;

import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.write.handler.CellWriteHandler;
import com.alibaba.excel.write.handler.context.CellWriteHandlerContext;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;

import java.util.Map;
import java.util.Set;

/**
 * @author Fred
 * @since 2025/8/24 23:22
 */
public class CustomCellColorHandler implements CellWriteHandler {

    private final Map<String, Set<String>> colorCells;

    public CustomCellColorHandler(Map<String, Set<String>> colorCells) {
        this.colorCells = colorCells;
    }

    @Override
    public void afterCellDispose(CellWriteHandlerContext context) {
        Cell cell = context.getCell();
        String sheetName = cell.getSheet().getSheetName();
        int rowIndex = cell.getRowIndex();
        int columnIndex = cell.getColumnIndex();

        if (colorCells.containsKey(sheetName) && colorCells.get(sheetName).contains(rowIndex + "," + columnIndex)) {
            WriteCellData<?> cellData = context.getFirstCellData();
            WriteCellStyle writeCellStyle = cellData.getOrCreateStyle();
            writeCellStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
            writeCellStyle.setFillPatternType(FillPatternType.SOLID_FOREGROUND);
        }
    }
}

