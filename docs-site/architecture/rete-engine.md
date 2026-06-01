---
title: RETE 引擎
---

# RETE 引擎

RuleForge 的核心执行引擎基于经典 RETE（Latin for "network"）算法实现，是最高效的前向推理规则匹配算法之一。

## RETE 算法原理

RETE 算法通过构建一个规则网络来高效匹配事实（Facts）与规则（Rules）：

```
ObjectTypeNode → AlphaNode → JoinNode → TerminalNode
```

### 网络结构

1. **ObjectTypeNode** — 按对象类型分类
2. **AlphaNode** — 测试单个属性的常量条件
3. **JoinNode (BetaNode)** — 测试多个属性之间的关系
4. **TerminalNode** — 规则触发点

### 优势

- **增量匹配**：当事实变化时，只重新计算受影响的部分网络
- **节点共享**：多个规则共享相同条件节点，减少重复计算
- **内存高效**：使用 token 存储部分匹配结果

## 知识会话

```
KnowledgePackage → KnowledgeSession → insert(facts) → fireAllRules()
```

### 会话生命周期

1. 从知识包创建会话
2. 插入业务数据（实体对象）
3. 触发规则执行
4. 收集输出结果
5. 销毁会话

## 性能特点

- 规则匹配时间复杂度：接近 O(1)（增量更新）
- 适用于规则数量多、频繁变化的场景
- 支持知识包缓存，避免重复编译
