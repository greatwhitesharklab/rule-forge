package com.ruleforge.executor.service.impl;

import com.ruleforge.builder.KnowledgeBuilder;
import com.ruleforge.runtime.cache.KnowledgeCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Feature: V5.18 executor → console 远程回调 baseUrl 修复
 *
 * <p><b>背景:</b> V5.18 之前 {@link KnowledgePackageServiceImpl#sendRequest}
 * 用裸 {@code consoleRestTemplate} + 相对路径 {@code /ruleforge/packageeditor/loadPackages},
 * 触发 {@code IllegalArgumentException: URI is not absolute}。
 * 这是 initial commit 起的 latent bug — {@code /test/do} 的远程回调路径
 * 实际从未真正 work 过,只是本地单测 + 单元集成绕过了。
 *
 * <p>本测试锁住 {@code sendRequest} 必须拼<b>绝对</b> URL 调 console,
 * 防止后续 PR 又把相对路径塞回来。
 */
@DisplayName("KnowledgePackageServiceImpl - V5.18 远程回调 baseUrl 修复")
class KnowledgePackageServiceImplTest {

    private final KnowledgeBuilder knowledgeBuilder = mock(KnowledgeBuilder.class);
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final KnowledgeCache knowledgeCache = mock(KnowledgeCache.class);

    @Nested
    @DisplayName("Given consoleUrl 拼绝对 URL")
    class AbsoluteUrl {

        @Test
        @DisplayName("Then sendRequest 调 restTemplate.exchange 时 URL 必须以 http:// 开头")
        void shouldUseAbsoluteUrl() {
            // Given console 返空 list — 走完流程,只关心 URL 是否被拼对
            when(restTemplate.exchange(
                    anyString(), any(HttpMethod.class), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                    .thenReturn(ResponseEntity.ok(List.of()));

            KnowledgePackageServiceImpl svc = new KnowledgePackageServiceImpl(
                    knowledgeBuilder, restTemplate, knowledgeCache, "http://console-host:8180");

            // 触发 sendRequest(包 id 必是 "project/pkg" 形式)
            try {
                svc.buildKnowledgePackage("proj/pkg01");
            } catch (Exception ignored) {
                // KnowledgeBuilder 后续会 throw,只关心 sendRequest 阶段
            }

            // 抓 URL
            ArgumentCaptor<String> urlCap = ArgumentCaptor.forClass(String.class);
            verify(restTemplate).exchange(
                    urlCap.capture(), any(HttpMethod.class), any(HttpEntity.class),
                    any(ParameterizedTypeReference.class));
            String usedUrl = urlCap.getValue();
            assertThat(usedUrl)
                    .as("sendRequest 必须用绝对 URL,不能用相对路径")
                    .startsWith("http://console-host:8180/");
            assertThat(usedUrl)
                    .as("URL 末尾应该是 loadPackages 端点")
                    .endsWith("/ruleforge/packageeditor/loadPackages");
        }

        @Test
        @DisplayName("Then consoleUrl 带尾部 / 时也要正确处理(不能拼出 //ruleforge)")
        void shouldStripTrailingSlash() {
            when(restTemplate.exchange(
                    anyString(), any(HttpMethod.class), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                    .thenReturn(ResponseEntity.ok(List.of()));

            KnowledgePackageServiceImpl svc = new KnowledgePackageServiceImpl(
                    knowledgeBuilder, restTemplate, knowledgeCache, "http://console-host:8180/");

            try {
                svc.buildKnowledgePackage("proj/pkg01");
            } catch (Exception ignored) {
            }

            ArgumentCaptor<String> urlCap = ArgumentCaptor.forClass(String.class);
            verify(restTemplate).exchange(
                    urlCap.capture(), any(HttpMethod.class), any(HttpEntity.class),
                    any(ParameterizedTypeReference.class));
            String usedUrl = urlCap.getValue();
            assertThat(usedUrl)
                    .as("consoleUrl 尾部 / 必须剥掉,不能拼出 //ruleforge")
                    .doesNotContain("//ruleforge")
                    .isEqualTo("http://console-host:8180/ruleforge/packageeditor/loadPackages");
        }
    }

    @Nested
    @DisplayName("Given sendRequest 相对路径(V5.18 之前的 bug)")
    class RelativeUrlRegression {

        @Test
        @DisplayName("Then 必须不出现以 /ruleforge 开头的相对路径(这种路径会抛 URI is not absolute)")
        void mustNotUseRelativePath() {
            when(restTemplate.exchange(
                    anyString(), any(HttpMethod.class), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                    .thenReturn(ResponseEntity.ok(List.of()));

            KnowledgePackageServiceImpl svc = new KnowledgePackageServiceImpl(
                    knowledgeBuilder, restTemplate, knowledgeCache, "http://console-host:8180");

            try {
                svc.buildKnowledgePackage("proj/pkg01");
            } catch (Exception ignored) {
            }

            ArgumentCaptor<String> urlCap = ArgumentCaptor.forClass(String.class);
            verify(restTemplate).exchange(
                    urlCap.capture(), any(HttpMethod.class), any(HttpEntity.class),
                    any(ParameterizedTypeReference.class));
            String usedUrl = urlCap.getValue();
            assertThat(usedUrl)
                    .as("绝对 URL 不应以 /ruleforge 开头(sendRequest 传的是完整 URL,不是 path)")
                    .doesNotStartWith("/ruleforge");
        }
    }
}
