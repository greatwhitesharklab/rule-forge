package com.ruleforge.decision.flow.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.ir.FlowDefinition;
import com.ruleforge.decision.flow.parser.BpmnXmlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * FlowDefinition 缓存 + HTTP 拉取。
 *
 * 主路径(executor-app 启动 evaluate):
 * 1. getOrLoad(flowId): 查本地缓存;没有就调 console /flow/load?file={flowId} 拿 BPMN XML
 * 2. 解析成 IR 后 put 缓存
 * 3. invalidate(flowId): console saveBpmn 后调过来清缓存
 */
@Component
public class FlowDefinitionRepo {

    private static final Logger log = LoggerFactory.getLogger(FlowDefinitionRepo.class);

    private final ConcurrentMap<String, FlowDefinition> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> loadLocks = new ConcurrentHashMap<>();
    private final BpmnXmlParser parser;
    private final RestTemplate consoleRestTemplate;
    private final String consoleUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FlowDefinitionRepo(BpmnXmlParser parser,
                              RestTemplate consoleRestTemplate,
                              @Value("${ruleforge.console.url}") String consoleUrl) {
        this.parser = parser;
        this.consoleRestTemplate = consoleRestTemplate;
        this.consoleUrl = consoleUrl.endsWith("/")
            ? consoleUrl.substring(0, consoleUrl.length() - 1) : consoleUrl;
    }

    public FlowDefinition get(String flowId) {
        return cache.get(flowId);
    }

    public void put(String flowId, FlowDefinition def) {
        cache.put(flowId, def);
    }

    public FlowDefinition getOrLoad(String flowId) {
        FlowDefinition def = cache.get(flowId);
        if (def != null) return def;
        Object lock = loadLocks.computeIfAbsent(flowId, k -> new Object());
        synchronized (lock) {
            def = cache.get(flowId);
            if (def != null) return def;
            String xml = loadBpmnXml(flowId);
            def = parser.parse(xml);
            cache.put(flowId, def);
            log.info("Loaded flow definition from console: flowId={}, nodes={}", flowId, def.getNodes().size());
            return def;
        }
    }

    public void invalidate(String flowId) {
        FlowDefinition removed = cache.remove(flowId);
        if (removed != null) {
            log.info("Invalidated flow definition: flowId={}", flowId);
        }
    }

    private String loadBpmnXml(String flowId) {
        String url = consoleUrl + "/ruleforge/flow/load?file="
            + URLEncoder.encode(flowId, StandardCharsets.UTF_8);
        try {
            ResponseEntity<String> resp = consoleRestTemplate.exchange(
                url, HttpMethod.GET, null, String.class);
            if (resp.getBody() == null || resp.getBody().isBlank()) {
                throw new FlowExecutionException("Empty BPMN XML from console for flowId=" + flowId);
            }
            return resp.getBody();
        } catch (RestClientException e) {
            throw new FlowExecutionException(
                "Failed to load BPMN from console for flowId=" + flowId + ": " + e.getMessage(), e);
        }
    }
}
