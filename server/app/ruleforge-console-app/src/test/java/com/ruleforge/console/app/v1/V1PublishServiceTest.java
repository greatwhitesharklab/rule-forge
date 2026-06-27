package com.ruleforge.console.app.v1;

import com.ruleforge.console.storage.GitStorageService;
import com.ruleforge.exception.RuleException;
import com.ruleforge.v1.ast.RuleAsset;
import com.ruleforge.v1.ast.RuleAssetIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Feature: V7.6 V1 原生发布 — 发布服务(V1PublishService)
 *
 * <p>背景:废弃老 .rp 知识包管线(审批/影子/批测),V1 决策流两态 draft / published,
 * 再发布覆盖当前版本(bundle 存 DB,git tag best-effort)。
 *
 * <p>发布链:V1BundleResolver.resolve → 序列化 bundle → rf_v1_publish upsert(首次 insert / 再发 updateById)
 * → git tag best-effort。无 git 仓时 tag 跳过,bundle 仍入库。
 */
@DisplayName("V1PublishService — 发布/状态")
class V1PublishServiceTest {

    private static final String FLOW_PATH = "/p/V1决策流/loan.v1flow.json";

    private V1PublishedBundle sampleBundle() {
        RuleAsset asset = new RuleAsset();
        asset.setId("a1");
        asset.setName("loan");
        return new V1PublishedBundle(asset, null, Collections.emptyMap());
    }

    @Nested
    @DisplayName("publish(flowPath, user)")
    class Publish {

        @Test
        @DisplayName("GIVEN 未发布(无行)WHEN publish THEN insert version=1.0.0 + bundle 入库 + status=published")
        void firstPublishInserts() throws Exception {
            V1BundleResolver resolver = mock(V1BundleResolver.class);
            V1PublishMapper mapper = mock(V1PublishMapper.class);
            GitStorageService git = mock(GitStorageService.class);
            when(resolver.resolve(FLOW_PATH)).thenReturn(sampleBundle());
            when(mapper.selectByFlow(eq("p"), eq(FLOW_PATH))).thenReturn(null);
            when(git.repoExists("p")).thenReturn(false);
            V1PublishService svc = new V1PublishService(resolver, mapper, git);

            V1PublishService.PublishResult result = svc.publish(FLOW_PATH, "admin");

            ArgumentCaptor<V1PublishEntity> captor = ArgumentCaptor.forClass(V1PublishEntity.class);
            verify(mapper).insert(captor.capture());
            verify(mapper, never()).updateById(any(V1PublishEntity.class));
            V1PublishEntity row = captor.getValue();
            assertThat(row.getCurrentVersion()).isEqualTo("1.0.0");
            assertThat(row.getProject()).isEqualTo("p");
            assertThat(row.getFlowPath()).isEqualTo(FLOW_PATH);
            assertThat(row.getPublishUser()).isEqualTo("admin");
            assertThat(row.getCurrentGitTag()).isNull(); // 无 git 仓 → 跳过 tag
            // bundle JSON 可反序列化回(闭包冻结可往返)
            assertThat(row.getPublishBundle()).isNotBlank();
            V1PublishedBundle roundTrip = RuleAssetIO.mapper().readValue(row.getPublishBundle(), V1PublishedBundle.class);
            assertThat(roundTrip.getAsset().getName()).isEqualTo("loan");
            // 返回结果
            assertThat(result.getVersion()).isEqualTo("1.0.0");
            assertThat(result.getStatus()).isEqualTo("published");
        }

        @Test
        @DisplayName("GIVEN 已发布(current_version=1.0.0)WHEN publish THEN updateById version=1.0.1")
        void rePublishUpdates() throws Exception {
            V1BundleResolver resolver = mock(V1BundleResolver.class);
            V1PublishMapper mapper = mock(V1PublishMapper.class);
            GitStorageService git = mock(GitStorageService.class);
            when(resolver.resolve(FLOW_PATH)).thenReturn(sampleBundle());
            V1PublishEntity existing = new V1PublishEntity();
            existing.setId(99L);
            existing.setCurrentVersion("1.0.0");
            when(mapper.selectByFlow(eq("p"), eq(FLOW_PATH))).thenReturn(existing);
            when(git.repoExists("p")).thenReturn(false);
            V1PublishService svc = new V1PublishService(resolver, mapper, git);

            V1PublishService.PublishResult result = svc.publish(FLOW_PATH, "admin");

            verify(mapper).updateById(existing);
            verify(mapper, never()).insert(any(V1PublishEntity.class));
            assertThat(existing.getCurrentVersion()).isEqualTo("1.0.1");
            assertThat(result.getVersion()).isEqualTo("1.0.1");
        }

        @Test
        @DisplayName("GIVEN 有 git 仓 WHEN publish THEN createTag(main) 被调,tag 含版本号")
        void publishCreatesGitTag() throws Exception {
            V1BundleResolver resolver = mock(V1BundleResolver.class);
            V1PublishMapper mapper = mock(V1PublishMapper.class);
            GitStorageService git = mock(GitStorageService.class);
            when(resolver.resolve(FLOW_PATH)).thenReturn(sampleBundle());
            when(mapper.selectByFlow(any(), any())).thenReturn(null);
            when(git.repoExists("p")).thenReturn(true);
            V1PublishService svc = new V1PublishService(resolver, mapper, git);

            V1PublishService.PublishResult result = svc.publish(FLOW_PATH, "admin");

            verify(git).createTag(eq("p"), eq("v1pub/p/loan/1.0.0"), eq("main"));
            assertThat(result.getGitTag()).isEqualTo("v1pub/p/loan/1.0.0");
        }
    }

    @Nested
    @DisplayName("status(flowPath)")
    class Status {
        @Test
        @DisplayName("GIVEN 未发布 THEN status=draft + currentVersion=null")
        void draft() {
            V1BundleResolver resolver = mock(V1BundleResolver.class);
            V1PublishMapper mapper = mock(V1PublishMapper.class);
            GitStorageService git = mock(GitStorageService.class);
            when(mapper.selectByFlow(any(), any())).thenReturn(null);
            V1PublishService svc = new V1PublishService(resolver, mapper, git);

            V1PublishService.PublishStatus st = svc.status(FLOW_PATH);

            assertThat(st.getStatus()).isEqualTo("draft");
            assertThat(st.getCurrentVersion()).isNull();
        }

        @Test
        @DisplayName("GIVEN 已发布 THEN status=published + currentVersion")
        void published() {
            V1BundleResolver resolver = mock(V1BundleResolver.class);
            V1PublishMapper mapper = mock(V1PublishMapper.class);
            GitStorageService git = mock(GitStorageService.class);
            V1PublishEntity row = new V1PublishEntity();
            row.setCurrentVersion("1.0.2");
            when(mapper.selectByFlow(any(), any())).thenReturn(row);
            V1PublishService svc = new V1PublishService(resolver, mapper, git);

            V1PublishService.PublishStatus st = svc.status(FLOW_PATH);

            assertThat(st.getStatus()).isEqualTo("published");
            assertThat(st.getCurrentVersion()).isEqualTo("1.0.2");
        }
    }

    @Nested
    @DisplayName("nextVersion — 版本号递增")
    class NextVersion {
        @Test
        void firstVersion() {
            assertThat(V1PublishService.nextVersion(null)).isEqualTo("1.0.0");
        }

        @Test
        void bumpFixDigit() {
            V1PublishEntity e = new V1PublishEntity();
            e.setCurrentVersion("1.0.0");
            assertThat(V1PublishService.nextVersion(e)).isEqualTo("1.0.1");
            e.setCurrentVersion("2.3.9");
            assertThat(V1PublishService.nextVersion(e)).isEqualTo("2.3.10");
        }

        @Test
        void malformedResets() {
            V1PublishEntity e = new V1PublishEntity();
            e.setCurrentVersion("weird");
            assertThat(V1PublishService.nextVersion(e)).isEqualTo("1.0.0");
        }
    }
}
