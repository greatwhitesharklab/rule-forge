package com.ruleforge.console.util;

import lombok.extern.slf4j.Slf4j;

/**
 * 内容比较工具类
 */
@Slf4j
public class CompareUtils {

    /**
     * 比较两个XML内容的差异
     *
     * @param oldContent 旧内容
     * @param newContent 新内容
     * @return 差异内容字符串
     */
    public static String compareContent(String oldContent, String newContent) {
//        try {
//            OutputFormat format = OutputFormat.createPrettyPrint();
//            format.setIndent("    ");
//            format.setEncoding("UTF-8");
//
//            // 老版本
//            Document document = DocumentHelper.parseText(oldContent);
//            StringWriter stringWriter = new StringWriter();
//            XMLWriter writer = new XMLWriter(stringWriter, format);
//            writer.write(document);
//            writer.close();
//            String[] lines = stringWriter.toString().split("\n");
//            List<String> oldLineList = new ArrayList<>(lines.length);
//            for (String str : lines) {
//                oldLineList.add(str.replaceAll("\\s+$", ""));
//            }
//
//            // 新版本
//            if (newContent.startsWith("%3C%3F")) {
//                newContent = Utils.decodeURL(newContent);
//            }
//            document = DocumentHelper.parseText(newContent);
//            stringWriter = new StringWriter();
//            writer = new XMLWriter(stringWriter, format);
//            writer.write(document);
//            writer.close();
//            lines = stringWriter.toString().split("\n");
//            List<String> newLineList = new ArrayList<>(lines.length);
//            for (String str : lines) {
//                newLineList.add(str.replaceAll("\\s+$", ""));
//            }
//
//            Patch<String> patch = DiffUtils.diff(oldLineList, newLineList);
//            StringBuilder sb = new StringBuilder();
//            for (AbstractDelta<String> delta : patch.getDeltas()) {
//                // 老文件
//                sb.append("old---\n");
//                for (String line : delta.getSource().getLines()) {
//                    sb.append(line).append("\n");
//                }
//                // 新文件
//                sb.append("new +++\n");
//                for (String line : delta.getTarget().getLines()) {
//                    sb.append(line).append("\n");
//                }
//            }
//            return sb.toString();
//        } catch (Exception e) {
//            log.warn("compareContent", e);
//        }

        return "null";
    }
}