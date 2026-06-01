---
title: 代码结构
---

# 代码结构

## 仓库结构

```
ruleforge/
├── server/                    # Java 后端
│   ├── ruleforge-core/       # 规则引擎核心
│   ├── ruleforge-decision/   # 共享决策模块
│   ├── ruleforge-console/    # 编辑器业务
│   ├── ruleforge-executor/   # 执行器业务
│   ├── ruleforge-console-app/# 可部署编辑器
│   └── ruleforge-executor-app/# 可部署执行器
├── console-ui/               # React 前端
├── model-service/            # Python ML 服务
├── docs/                     # GitHub 源码文档
├── docs-site/                # VitePress 文档站
├── demo/                     # 金融场景 Demo
├── scripts/                  # 启动脚本
└── docker/                   # Docker 配置
```

## 后端核心包结构

### ruleforge-core

```
com.ruleforge
├── model/          # 规则模型（Rule, DecisionTable, Scorecard...）
├── model.rete/     # RETE 算法实现
├── runtime/        # 知识会话、执行、缓存
├── parse/          # XML/DSL 解析器（ANTLR4）
└── controller/     # Servlet 入口
```

### ruleforge-console

```
com.ruleforge.console
├── controller/     # REST 控制器
├── flow/           # Flowable 8 集成
├── service/        # 业务服务
├── storage/        # 项目存储
├── mapper/         # MyBatis-Plus Mapper
├── repository/     # 数据模型
└── config/         # 配置类
```

## 前端核心目录

```
console-ui/src/
├── frame/          # 主框架布局
├── editor/         # 规则编辑器
├── flow-bpmn/      # BPMN 流程设计器
├── datasource/     # 数据源管理
├── monitoring/     # 监控面板
├── analysis/       # 分析页面
├── agent/          # AI 助手
└── api/            # API 客户端
```
