# Agent Collaborate

AI Agent 协作开发平台后端。当前已完成第一阶段：Spring Boot 骨架、PostgreSQL/Flyway、用户、项目、项目成员、能力画像、JWT 登录和项目级基础权限。

产品与架构约束以 [docs/README.md](docs/README.md) 中列出的冻结文档和 ADR 为准。

## 环境要求

- Java 17+
- Maven 3.9+
- Docker Desktop（用于 PostgreSQL 集成测试）
- PostgreSQL 16（本地运行）

所有数据库凭证和 JWT 密钥必须通过环境变量提供，参考 `.env.example` 的变量名；不要提交实际值。

## 验证

```powershell
mvn -s .mvn/settings.xml test
```

测试通过 Testcontainers 启动 PostgreSQL 16，并在空库执行全部 Flyway migration。

## 本地运行

配置 `DB_PASSWORD` 后可启动数据库：

```powershell
docker compose up -d postgres
```

配置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 和至少 32 字节的 `JWT_SECRET` 后启动 API：

```powershell
mvn -s .mvn/settings.xml spring-boot:run
```

OpenAPI 文档路径为 `/api/openapi`，Swagger UI 路径为 `/api/swagger-ui`。
