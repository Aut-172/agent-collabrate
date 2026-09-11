# Agent Collaborate

面向小型开发团队的 Agent 协作编排平台 MVP。平台负责需求、版本化文档、任务包、项目级分工、Git/CI 事实同步、通知和审计；成员在本地使用自己的 Agent 和 Git 工具完成代码修改。平台不访问成员本地工作区，也不在服务器执行成员代码。

## 当前进度

当前 `main` 已包含：

- User、JWT、Project、ProjectMember 和版本化能力画像；
- Workflow、DocumentVersion、状态机、Intent 层级和 CI Bootstrap；
- Repo Inventory、Context Plan、受控多轮代码取证和 Code Context；
- AgentRun、OutboxJob、Mock Agent Provider；
- Build Plan、Task、TaskAssignment、任务包版本确认和 Final Report/TaskDelivery；
- GitHub Git/Actions 只读 Adapter，以及 Git/CI SHA 强绑定；
- 任务看板、站内通知、Task Blocker 完整流程；
- GitHub Webhook HMAC 验签、delivery 幂等和轮询兜底；
- V19 不可变 AuditLog、项目权限隔离、分页查询和敏感字段脱敏；
- `frontend/` 下的 Vite + React 最小管理界面。

数据库结构由 Flyway V1-V19 管理，禁止修改已推送 migration；后续结构从 V20 开始。

## 启动方法

### 后端

要求：Java 17+、Maven 3.9+、PostgreSQL 14+。使用 PowerShell 时可按以下步骤启动：

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/agent_collab"
$env:DB_USERNAME = "agent_collab"
$env:DB_PASSWORD = "change-me"
$env:JWT_SECRET = "replace-with-at-least-32-random-bytes"
mvn spring-boot:run
```

可选配置：`GIT_PROVIDER`、`GIT_API_URL`、`GIT_TOKEN`、`CI_WEBHOOK_SECRET`、Agent Provider 相关环境变量。生产环境必须通过环境变量或密钥管理服务提供凭证，不要写入代码、任务包、日志或数据库。

后端 API：<http://localhost:8080>。OpenAPI：<http://localhost:8080/api/openapi>，Swagger UI：<http://localhost:8080/api/swagger-ui>。

### 前端

```powershell
cd frontend
npm install
npm run dev
```

前端开发地址：<http://localhost:5173>。Vite 已将 `/api` 请求代理到 `http://localhost:8080`。生产构建使用：

```powershell
npm run build
```

### 测试

后端全量测试需要 Docker Desktop 运行，以便 Testcontainers 启动 PostgreSQL：

```powershell
mvn test
```

前端构建检查：

```powershell
cd frontend
npm run build
```

## 文档导航

完整的产品、状态机、技术、交接、集成、安全、验收和前端规范位于 [docs/README.md](docs/README.md)。推荐先阅读该文档，再按其中的 01-15 冻结文档顺序阅读。

## 设计边界

- 外部 Agent、Git、CI 调用使用数据库 Outbox 异步执行并保留运行事实；
- Git/CI 成功结果必须绑定当前 TaskDelivery 的真实 Commit SHA；
- 文档、任务、任务包、分配、交付、通知和审计历史不覆盖、不物理删除；
- 只有项目成员能访问项目资源，Leader 治理权限不能跨项目使用；
- Mock Provider 仅用于测试，生产 GitHub Provider 通过环境变量配置。
