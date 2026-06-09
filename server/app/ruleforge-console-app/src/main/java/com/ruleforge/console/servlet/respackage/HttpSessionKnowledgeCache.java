package com.ruleforge.console.servlet.respackage;

import jakarta.servlet.http.HttpServletRequest;

public class HttpSessionKnowledgeCache {

    public void put(HttpServletRequest req, String key, Object value) {
        req.getSession().setAttribute(key, value);
    }

    public Object get(HttpServletRequest req, String key) {
        return req.getSession().getAttribute(key);
    }

    public void remove(HttpServletRequest req, String key) {
        req.getSession().removeAttribute(key);
    }
}
