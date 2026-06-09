package com.ruleforge.executor.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Feature: V5.18 {@link ExecResourceProvider} 远程回调 baseUrl 修复
 *
 * <p>同 {@link KnowledgePackageServiceImpl} 同一 latent bug —
 * sendRequest 用相对路径 {@code /ruleforge/frame/fileSource} 会抛
 * "URI is not absolute"。本测试锁住 URL 必须是绝对的。
 */
@DisplayName("ExecResourceProvider - V5.18 远程回调 baseUrl 修复")
class ExecResourceProviderTest {

    @Test
    @DisplayName("provide 时调 console URL 必须是 http://.../ruleforge/frame/fileSource(绝对路径)")
    void shouldUseAbsoluteConsoleUrl() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.exchange(
                anyString(), any(HttpMethod.class), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(Map.of("content", "<rule/>")));

        ExecResourceProvider provider = new ExecResourceProvider(rt, "http://console-host:8180");

        provider.provide("/proj/rule01.rl", "LATEST", "LATEST", true);

        ArgumentCaptor<String> urlCap = ArgumentCaptor.forClass(String.class);
        verify(rt).exchange(
                urlCap.capture(), any(HttpMethod.class), any(HttpEntity.class),
                any(ParameterizedTypeReference.class));
        assertThat(urlCap.getValue())
                .as("ExecResourceProvider 必须用绝对 URL 调 console")
                .isEqualTo("http://console-host:8180/ruleforge/frame/fileSource");
    }

    @Test
    @DisplayName("consoleUrl 带尾部 / 时也要正确处理")
    void shouldStripTrailingSlash() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.exchange(
                anyString(), any(HttpMethod.class), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(Map.of("content", "<rule/>")));

        ExecResourceProvider provider = new ExecResourceProvider(rt, "http://console-host:8180/");

        provider.provide("/proj/rule01.rl", "LATEST", "LATEST", true);

        ArgumentCaptor<String> urlCap = ArgumentCaptor.forClass(String.class);
        verify(rt).exchange(
                urlCap.capture(), any(HttpMethod.class), any(HttpEntity.class),
                any(ParameterizedTypeReference.class));
        assertThat(urlCap.getValue())
                .as("consoleUrl 尾部 / 必须剥掉,不能拼出 //ruleforge")
                .doesNotContain("//ruleforge")
                .isEqualTo("http://console-host:8180/ruleforge/frame/fileSource");
    }
}
