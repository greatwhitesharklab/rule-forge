package com.ruleforge.debug;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class DefaultHtmlFileDebugWriter implements DebugWriter {
    private String path;

    @Override
    public void write(List<MessageItem> items) throws IOException {
        if (StringUtils.isBlank(path)) {
            return;
        }
        StringBuilder msg = new StringBuilder();
        for (MessageItem item : items) {
            msg.append(item.toHtml());
        }
        String fullPath = path + "/ruleforge-debug.html";
        File file = new File(fullPath);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        FileOutputStream out = new FileOutputStream(file);
        String sb = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>RuleForge调试日志信息</title><body style='font-size:12px'>" +
                msg.toString() +
                "</body></html>";
        IOUtils.write(sb, out);
        out.flush();
        out.close();
    }

    public void setPath(String path) {
        this.path = path;
    }
}
