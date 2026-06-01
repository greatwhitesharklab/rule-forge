---
title: 贡献指南
---

# 贡献指南

感谢你对 RuleForge 的关注！我们欢迎各种形式的贡献。

## 贡献方式

- [提交 Bug](https://github.com/FredGoo/rule-forge/issues/new?template=bug_report.md)
- [功能建议](https://github.com/FredGoo/rule-forge/issues/new?template=feature_request.md)
- 改进文档
- 提交代码

## 开发流程

### 1. Fork & Branch

```bash
# Fork 后克隆
git clone https://github.com/YOUR_USERNAME/rule-forge.git

# 创建功能分支
git checkout -b feature/your-feature
```

### 2. 开发 & 测试

```bash
# 编译
cd server && mvn compile

# 运行测试
mvn test

# 前端测试
cd ../console-ui && npm test
```

### 3. 提交 PR

1. 确保所有测试通过
2. 更新相关文档
3. 填写 PR 模板中的检查清单
4. 提交 Pull Request 到 `main` 分支

## 代码规范

### Java

- 遵循阿里巴巴 Java 开发手册
- 使用 4 空格缩进
- 公共方法必须有 Javadoc

### TypeScript

- 使用 ESLint + Prettier
- 使用 2 空格缩进
- 组件使用函数式写法

### Git Commit

使用 [Conventional Commits](https://www.conventionalcommits.org/) 格式：

```
feat: 添加评分卡导入功能
fix: 修复决策表条件匹配错误
docs: 更新 API 文档
```

## 行为准则

参见 [CODE_OF_CONDUCT.md](https://github.com/FredGoo/rule-forge/blob/main/CODE_OF_CONDUCT.md)。
