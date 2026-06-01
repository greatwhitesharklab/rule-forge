---
title: Executor API
---

# Executor API

Executor App（端口 8280）提供规则执行的 REST API。

## 基础信息

- **Base URL**: `http://localhost:8280`
- **Content-Type**: `application/json`

## 规则测试执行

### 执行测试

```
POST /test/do
Content-Type: application/json

{
  "project": "项目名称",
  "packageId": "知识包ID",
  "data": {
    "field1": "value1",
    "field2": 100
  }
}
```

### 获取知识包信息

```
GET /test/knowledge?project=项目名称&packageId=知识包ID
```
