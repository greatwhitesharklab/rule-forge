---
title: Console API
---

# Console API

Console App（端口 8180）提供规则编辑和管理的 REST API。

## 基础信息

- **Base URL**: `http://localhost:8180/ruleforgeV2`
- **Content-Type**: `application/json`

## 项目管理

### 获取项目列表

```
GET /frame/loadProjects
```

### 保存项目

```
POST /frame/saveProject
Content-Type: application/json

{
  "name": "项目名称",
  "description": "项目描述"
}
```

### 删除项目

```
DELETE /frame/deleteProject?name=项目名称
```

## 规则文件管理

### 保存规则文件

```
POST /frame/saveFile
Content-Type: application/json

{规则文件 JSON 内容}
```

### 导出项目

```
GET /frame/exportProject?name=项目名称
```

## 知识包管理

### 发布知识包

```
POST /frame/publishPackage
Content-Type: application/json

{
  "project": "项目名称",
  "version": "1.0.0"
}
```

### 获取知识包版本

```
GET /frame/loadVersions?project=项目名称
```
