---
title: 第一个规则
---

# 第一个规则

本教程将带你创建一条简单的贷款审批规则，体验 RuleForge 的核心功能。

## 前置条件

确保 RuleForge 已经启动（参见[快速开始](/guide/getting-started)）。

## Step 1: 登录

打开 http://localhost，进入 RuleForge 控制台。

## Step 2: 创建项目

1. 点击左侧导航栏「项目管理」
2. 点击「新建项目」
3. 输入项目名称，如 "我的第一个规则"
4. 点击「确定」

## Step 3: 创建变量库

变量库定义了规则引擎需要处理的业务数据。

1. 在项目中点击「变量库」
2. 添加以下变量：
   - `applicantName` (字符串) - 申请人姓名
   - `loanAmount` (数值) - 贷款金额
   - `creditScore` (数值) - 信用评分
   - `result` (字符串) - 审批结果（输出）

## Step 4: 创建决策表

1. 点击「新建规则」→ 选择「决策表」
2. 配置条件列和动作列：
   - 条件：creditScore >= 750 → 动作：result = "通过"
   - 条件：creditScore >= 600 AND creditScore < 750 → 动作：result = "人工审核"
   - 条件：creditScore < 600 → 动作：result = "拒绝"

## Step 5: 测试规则

1. 点击「快速测试」
2. 输入测试数据：
   ```json
   {
     "applicantName": "张三",
     "loanAmount": 500000,
     "creditScore": 780
   }
   ```
3. 点击「执行」，查看结果

## Step 6: 发布

测试通过后，点击「发布知识包」将规则部署到执行器。

## 下一步

- [规则类型详解](/guide/rule-types) — 了解全部 7 种规则类型
- [小微信贷审批教程](/tutorial/sme-loan-approval) — 完整金融场景实战
