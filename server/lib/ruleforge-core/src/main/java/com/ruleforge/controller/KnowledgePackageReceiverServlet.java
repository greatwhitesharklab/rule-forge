package com.ruleforge.controller;

import com.ruleforge.Configure;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgePackageWrapper;
import com.ruleforge.runtime.cache.CacheUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Map;

@Slf4j
public class KnowledgePackageReceiverServlet extends HttpServlet {
    private static final long serialVersionUID = -4342175088856372588L;
    public static final String URL = "/knowledgepackagereceiver";

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String packageId = req.getParameter("packageId");
        if (StringUtils.isEmpty(packageId)) {
            return;
        }
        packageId = URLDecoder.decode(packageId, "utf-8");
        if (packageId.startsWith("/")) {
            packageId = packageId.substring(1);
        }
        String content = req.getParameter("content");
        if (StringUtils.isEmpty(content)) {
            return;
        }
        content = URLDecoder.decode(content, "utf-8");
        ObjectMapper mapper = new ObjectMapper();
        mapper.setDateFormat(new SimpleDateFormat(Configure.getDateFormat()));
        KnowledgePackageWrapper wrapper = mapper.readValue(content, KnowledgePackageWrapper.class);
        wrapper.buildDeserialize();
        KnowledgePackage knowledgePackage = wrapper.getKnowledgePackage();
        CacheUtils.getKnowledgeCache().putKnowledge(packageId, knowledgePackage);
        log.info("Successfully receive the server side to pushed package: {}", packageId);
        resp.setContentType("text/plain");
        PrintWriter pw = resp.getWriter();
        pw.write("ok");
        pw.flush();
        pw.close();
    }
}
