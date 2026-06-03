---
title: 生产环境部署
---

# 生产环境部署

## 架构建议

生产环境建议部署多个 Executor 实例，通过负载均衡分发请求：

```
                   ┌─────────────┐
                   │  Nginx/LB   │
                   └──────┬──────┘
                          │
              ┌───────────┼───────────┐
              ▼           ▼           ▼
     ┌──────────────┐ ┌──────────────┐
     │ Executor #1  │ │ Executor #2  │ ...
     └──────────────┘ └──────────────┘
              │           │
              └─────┬─────┘
                    │
             ┌──────▼──────┐
             │ Console App  │ (单实例)
             └──────┬──────┘
                    │
             ┌──────▼──────┐
             │    MySQL     │ (主从)
             └─────────────┘
```

## 性能调优

### JVM 参数

```bash
# Console App（编辑器，内存需求较大）
JAVA_OPTS="-Xmx4096m -Xms2048m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Executor App（执行器，追求低延迟）
JAVA_OPTS="-Xmx2048m -Xms1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=50"
```

### 数据库

- 使用 MySQL 8.0+ 并启用连接池（HikariCP）
- 建议最大连接数 50-100
- 启用慢查询日志

### 监控

RuleForge 内置 Micrometer + Prometheus 指标,两个 app 都暴露 `/actuator/prometheus` 端点(commit `ba08c2b` 起可用):

```yaml
# Prometheus 配置
scrape_configs:
  - job_name: 'ruleforge'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['console-app:8180', 'executor-app:8280']
```

> 其他可用端点(通过 `management.endpoints.web.exposure.include: health,info,metrics,prometheus` 暴露):
> - `/actuator/health` — 容器健康检查 + 3 个 DataSource 的 `isValid()` 探活
> - `/actuator/health/liveness` + `/actuator/health/readiness` — K8s 风格探针
> - `/actuator/info` — Flowable 版本等信息

## 安全建议

- 数据库密码使用 Docker Secrets 或外部密钥管理
- Console API 仅允许内网访问
- 启用 HTTPS（Nginx 终端 SSL）
- 定期备份 MySQL 数据
