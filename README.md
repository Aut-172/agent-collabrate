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

### Docker Compose（推荐）

要求：Docker Desktop 或兼容 Docker Engine，且支持 Docker Compose V2。首次启动：

```powershell
Copy-Item .env.example .env
# 修改 .env，至少替换 DB_PASSWORD 和 JWT_SECRET 示例值
docker compose up --build -d
docker compose ps
```

启动完成后访问前端 <http://localhost:5173>。后端 API 仍可通过 <http://localhost:8080> 直接访问；前端 Nginx 会将 `/api` 请求代理到后端容器。后端启动时自动执行 Flyway V1-V19。

查看日志和停止服务：

```powershell
docker compose logs -f backend frontend
docker compose down
```

PostgreSQL 数据保存在 `agent-collab-postgres` 命名卷中，普通 `docker compose down` 不会删除数据。不要在 `.env` 中使用示例密码部署生产环境，也不要提交 `.env`。

如果该 Compose 项目以前已经创建过 PostgreSQL 卷，之后修改 `.env` 中的 `DB_PASSWORD` 不会自动修改数据库角色密码。出现 `password authentication failed for user "agent_collab"` 时，可保留现有数据并同步密码：

```powershell
.\scripts\sync-compose-db-password.ps1
```

脚本从 PostgreSQL 容器环境读取当前配置，不会在命令行或日志中输出密码。它会更新数据库角色密码，并等待后端和前端恢复健康。若确认本地数据库内容可以全部丢弃，也可以执行 `docker compose down --volumes` 后重新启动；该命令会永久删除 Compose 数据卷及其中的数据。

### 后端

不使用容器时，要求 Java 17+、Maven 3.9+、PostgreSQL 14+。使用 PowerShell 时可按以下步骤启动：

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/agent_collab"
$env:DB_USERNAME = "agent_collab"
$env:DB_PASSWORD = "change-me"
$env:JWT_SECRET = "replace-with-at-least-32-random-bytes"
mvn spring-boot:run
```

可选配置：`GIT_PROVIDER`、`GIT_API_URL`、`GIT_TOKEN`、`CI_WEBHOOK_SECRET`、Agent Provider 相关环境变量。生产环境必须通过环境变量或密钥管理服务提供凭证，不要写入代码、任务包、日志或数据库。

当前支持通过 OpenAI Responses API 调用平台 Agent。启用真实 Provider 时配置：

```powershell
$env:AGENT_PROVIDER = "openai"
$env:AGENT_API_URL = "https://api.openai.com/v1/responses"
$env:AGENT_API_KEY = "<server-side-api-key>"
$env:AGENT_MODEL = "<model-id>"
mvn spring-boot:run
```

API Key 只应存在于后端环境变量或密钥管理服务，不要放入前端、任务包、日志或数据库。未设置 `AGENT_PROVIDER=openai` 时，生产环境使用显式未配置占位 Provider；仅设置 API Key 不会自动启用真实调用。

在正式 Workflow 之外做一次单独 Provider 验证时，可显式启用诊断接口：

```powershell
$env:AGENT_DIAGNOSTICS_ENABLED = "true"
$env:SPRING_PROFILES_ACTIVE = "agent-diagnostics"
mvn spring-boot:run
```

然后向 `POST /api/agent-diagnostics/generate` 发送一个 `AgentGenerationRequest` JSON。接口直接返回回显的结构化输入、Provider 输出格式和原始输出文本，不创建 AgentRun、不修改 Workflow；仍需要登录 JWT。该接口默认关闭，不建议在公网生产环境启用。

Build Plan 的 Prompt 会明确要求使用后端 `build-plan-v1.schema.json` 的字段：顶层必须是 `intentLevel`、`staffingRecommendation`、`tasks`、`assignments`、`alternatives`、`warnings`；任务使用 `effortPoints`、`verificationCommands`，分配使用 `userId`、`fitReason` 等字段。模型输出仍会经过服务端 JSON Schema 校验，不符合协议的结果会使 AgentRun 失败，不会创建任务。

后端 API：<http://localhost:8080>。OpenAPI：<http://localhost:8080/api/openapi>，Swagger UI：<http://localhost:8080/api/swagger-ui>。

首次使用可在前端登录页切换到“注册”，或调用 `POST /api/auth/register` 创建账号；注册成功会直接返回 JWT。用户名长度为 3-50 个字符，密码长度为 8-128 个字符。

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
npm test
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
