package com.ruleforge.console.servlet.respackage;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * V7.7.2:HttpSessionKnowledgeCache stub — 老 .rp 知识包 HTTP session 缓存。
 * .rp 废弃后保留接口签名(get/put/remove with HttpServletRequest + key)以维持
 * ReteDiagramController / TestController 等老 .rp 测试桩端点编译。
 * 实现返 null / no-op — 老 .rp 测试桩端点不再有意义,V1 决策流不依赖此缓存。
 */
@Slf4j
@Component
public class HttpSessionKnowledgeCache {

    public Object get(HttpServletRequest request, String key) {
        return null;
    }

    public void put(HttpServletRequest request, String key, Object value) {
        // no-op
    }

    public void remove(HttpServletRequest request, String key) {
        // no-op
    }
}
