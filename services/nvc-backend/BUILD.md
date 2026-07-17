# nvc-backend — 构建与运行

## 前置条件
- Java 21
- PostgreSQL（含 pgvector 扩展）
- Redis

## 构建
```bash
./gradlew build
```

## 运行
```bash
./gradlew bootRun
```

## 测试
```bash
./gradlew test
```

## 环境变量
- 配置文件：`application.yml` + `.env`
- 敏感信息（API Key、DB 密码）放 `.env`，不入版本控制

## 验证
- 启动后访问 `http://localhost:8080/api/nvc/health` 确认服务正常
- 检查 Redis 连接：日志中无 Redis 连接错误
- 检查 DB 连接：JPA `ddl-auto: update` 自动建表无报错
