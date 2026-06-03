package com.ruleforge.console.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class RequestContext {

    private final HttpServletRequest req;
    private final HttpServletResponse resp;

    public RequestContext(HttpServletRequest req, HttpServletResponse resp) {
        this.req = req;
        this.resp = resp;
    }

    public HttpServletRequest getRequest() {
        return req;
    }

    public HttpServletResponse getResponse() {
        return resp;
    }
}
