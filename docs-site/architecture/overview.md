---
title: 架构概览
---

# 架构概览

## 系统架构

RuleForge 采用编辑器-执行器分离架构，支持水平扩展：

```
┌─────────────┐     ┌──────────────┐
│  Console UI  │────▶│ Console App  │  编辑器后端（端口 8180）
│  (React)     │     │  (Spring)    │
└─────────────┘     └──────┬───────┘
                           │
             ┌─────────────┼─────────────┐
             ▼             ▼              ▼
     ┌──────────────┐ ┌──────────┐ ┌──────────┐
     │ Executor App │ │  Model   │ │  MySQL   │
     │ (执行, 8280) │ │ Service  │ │ (存储)    │
     └──────────────┘ │ (ML,8501)│ └──────────┘
                      └──────────┘
```

## 模块结构

| 模块 | 说明 |
|------|------|
| ruleforge-core | 规则引擎核心（RETE 算法、规则解析、知识库） |
| ruleforge-decision | 共享决策模块（数据源、灰度策略、陪跑配置）。在 console-app 和 executor-app 中都作为嵌套 jar 引用 |
| ruleforge-console-app | 可部署的编辑器应用（Spring Boot，业务代码合入） |
| ruleforge-executor-app | 可部署的执行器应用（Spring Boot，业务代码合入） |

> 2026-05 历史变更:原独立的 `ruleforge-console` / `ruleforge-executor` 子模块已合入 `console-app` / `executor-app`(commit `5f01ebe5` / `f963fd5`),Maven 4 模块结构精简到当前 4 个。

依赖链：

```
core ← decision ← console ← console-app
core ← executor ← executor-app ← decision
```

## 核心流程

### 规则编辑流程

1. 用户在 Console UI 中编辑规则
2. Console App 保存规则到数据库
3. 发布时生成知识包（Knowledge Package）
4. 知识包推送到 Executor

### 规则执行流程

1. 业务系统调用 Executor API
2. Executor 加载知识包
3. 创建 RETE 会话，插入业务数据
4. 执行规则匹配
5. 收集结果返回

## 关键特性

### 灰度发布

支持应用层灰度路由：
- 用户比例灰度
- 随机百分比灰度
- 白名单灰度

### 影子测试

流量重放到影子规则包，自动对比主/陪跑差异：
- 4 个维度 × 4 级严重度
- 不影响线上服务
- 自动偏差检测和告警

### AI Agent 分析

内置 AI Agent 提供决策分析：
- 决策日志聚合和趋势分析
- 规则覆盖率检测
- 偏差检测和告警
- 优化建议
