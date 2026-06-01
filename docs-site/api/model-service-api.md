---
title: Model Service API
---

# Model Service API

Model Service（端口 8501）提供 ML 模型管理和推理的 REST API。

## 基础信息

- **Base URL**: `http://localhost:8501`
- **Content-Type**: `application/json`

## 健康检查

### 健康状态

```
GET /health
```

Response:
```json
{
  "status": "healthy",
  "loaded_models": 2
}
```

## 模型管理

### 上传模型

```
POST /models
Content-Type: multipart/form-data

file: <PKL 文件>
model_id: "model_name"
name: "Model Display Name"
```

### 列出模型

```
GET /models
```

### 获取模型信息

```
GET /models/{model_id}
```

### 激活模型

```
POST /models/{model_id}/activate
```

### 删除模型

```
DELETE /models/{model_id}
```

## 模型推理

### 预测

```
POST /predict/{model_id}
Content-Type: application/json

{
  "features": {
    "feature1": 1.5,
    "feature2": 100,
    "feature3": "value"
  }
}
```

Response:
```json
{
  "model_id": "model_name",
  "prediction": 0.85,
  "probabilities": {
    "0": 0.15,
    "1": 0.85
  }
}
```
