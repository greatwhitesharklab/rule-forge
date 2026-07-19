package com.ruleforge.datasource.connector;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 数据源连接器共用 HTTP 工厂。
 *
 * <p>背景:连接器原先注入 app 级共享 RestTemplate(new RestTemplate() 默认无超时),
 * 对端不响应时连接测试会一直挂到 OS TCP 超时(实测 ~65s),前端"测试中..."卡死。
 * 连接器调的是任意外部 REST,必须有界超时。
 */
final class ConnectorHttp {

    /** 建连超时:外部 API 不可达时 5s 内失败 */
    static final int CONNECT_TIMEOUT_MS = 5_000;
    /** 读超时:对端挂起时 10s 内失败 */
    static final int READ_TIMEOUT_MS = 10_000;

    private ConnectorHttp() {
    }

    static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }
}
