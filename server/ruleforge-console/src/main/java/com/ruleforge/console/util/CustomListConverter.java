package com.ruleforge.console.util;

import com.alibaba.excel.converters.Converter;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.GlobalConfiguration;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.metadata.property.ExcelContentProperty;

import java.util.ArrayList;

/**
 * @author Fred
 * @since 2025/8/25 15:37
 */
public class CustomListConverter implements Converter<ArrayList> {

    @Override
    public Class<ArrayList> supportJavaTypeKey() {
        return ArrayList.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }

    @Override
    public ArrayList convertToJavaData(ReadCellData<?> cellData, ExcelContentProperty contentProperty, GlobalConfiguration globalConfiguration) throws Exception {
        String stringValue = cellData.getStringValue();
        // 处理[aa,bb]格式，去掉方括号
        if (stringValue.startsWith("[") && stringValue.endsWith("]")) {
            stringValue = stringValue.substring(1, stringValue.length() - 1);
        }
        String[] split = stringValue.split(",");
        ArrayList enterpriseList = new ArrayList<>();
        for (String item : split) {
            enterpriseList.add(item.trim()); // 去掉可能的空格
        }
        return enterpriseList;
    }

    @Override
    public WriteCellData<String> convertToExcelData(ArrayList list, ExcelContentProperty contentProperty, GlobalConfiguration globalConfiguration) throws Exception {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("[");
        for (int i = 0; i < list.size(); i++) {
            stringBuilder.append(list.get(i).toString());
            if (i < list.size() - 1) {
                stringBuilder.append(",");
            }
        }
        stringBuilder.append("]");
        return new WriteCellData(stringBuilder.toString());
    }
}
