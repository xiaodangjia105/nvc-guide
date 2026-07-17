# API 契约

> 前后端之间的接口约定。改动属契约级，必须前后端同步。

## 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

- `code`：业务状态码（200 成功，其他为错误码）
- `message`：用户可读的消息
- `data`：业务数据，类型由具体接口决定

## 错误码分域

| 域 | 范围 | 示例 |
|----|------|------|
| 通用 | 1xxx | BAD_REQUEST(400)、NOT_FOUND(404) |
| NVC 练习 | 3xxx | NVC_SESSION_NOT_FOUND(3001)、NVC_EVALUATION_FAILED(3002) |
| 存储 | 4xxx | STORAGE_UPLOAD_FAILED(4001) |
| 导出 | 5xxx | EXPORT_PDF_FAILED(5001) |
| 知识库 | 6xxx | KNOWLEDGE_BASE_NOT_FOUND(6001) |
| AI 服务 | 7xxx | AI_SERVICE_TIMEOUT(7002) |
| 限流 | 8xxx | RATE_LIMIT_EXCEEDED(8001) |
| NVC 档案 | 3xxx | NVC_PROFILE_NOT_FOUND(3004) |
| NVC 语音 | 10xxx | NVC_VOICE_SESSION_NOT_FOUND(10001) |

## 分页

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [],
    "totalElements": 100,
    "totalPages": 10,
    "number": 0,
    "size": 20
  }
}
```

## API 路径规范
- 后端前缀：`/api/nvc/{module}/{action}`
- RESTful 风格：GET 查询、POST 创建/操作、PUT 更新、DELETE 删除
