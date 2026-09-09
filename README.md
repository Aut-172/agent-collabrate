# Agent Collaborate

AI Agent 协作开发编排平台后端。平台负责项目治理、Intent 规划、平台侧 Agent 文档生成、任务分配和运行记录，不访问成员本地代码，也不在服务器执行成员代码。

产品与架构约束以 [docs/README.md](docs/README.md) 中列出的冻结文档和 ADR 为准。

## 当前实现

- 用户初始化、登录、JWT 鉴权和 BCrypt 密码哈希；
- Project、ProjectMember 和项目级 Leader/Member 权限隔离；
- 属于 ProjectMember 的版本化能力画像、可投入状态和每周容量；
- Architecture、Feature、Change 三种 Intent 及差异化 Workflow 状态机；
- Design、Spec、Build Plan 不可变文档版本、编辑、确认和候选版本重新生成；
- AgentRun、OutboxJob、定时 Worker、超时回收、自动/人工重试和取消；
- Intent 感知的 Build Plan JSON Schema 和 Mock Agent Provider；
- Build Plan 草稿编辑、Leader 审批和批准版本绑定；
- 从批准 Plan 幂等创建 Task、子 Intent 和 TaskAssignment；
- TaskAssignment 分配历史，以及分配时的画像版本、画像快照、工作量快照、理由和评分；
- Leader 和 Member 均可成为任务负责人，Architecture 不创建开发 Task 或 TaskAssignment。

尚未实现 Task Package、任务包确认、Blocker、Final Report/TaskDelivery、Git/PR/CI Provider、Commit SHA 校验、审计通知和管理前端。

## 核心流程

```text
Intent -> Design -> Spec -> Build Plan -> Leader Approval
  Architecture -> Child Intents / Architecture Baseline
  Feature/Change -> Tasks -> TaskAssignments -> Local Agent（后续阶段）

生成请求 -> AgentRun + OutboxJob -> HTTP 202
Worker -> Provider -> Schema 校验 -> DocumentVersion -> Workflow 推进
```

所有状态转换位于 Service/Domain 层。文档、画像版本、Agent 运行和任务分配历史不会被静默覆盖。

## 主要 API

```text
POST /api/auth/initialize
POST /api/auth/login
GET  /api/me

POST /api/projects
GET  /api/projects
POST /api/projects/{id}/members
PUT  /api/projects/{id}/members/me/profile

POST /api/projects/{id}/workflows
GET  /api/workflows/{id}
POST /api/workflows/{id}/generate-design
POST /api/workflows/{id}/generate-spec
POST /api/workflows/{id}/generate-build-plan
PUT  /api/workflows/{id}/plan-drafts
POST /api/workflows/{id}/approve-plan
POST /api/workflows/{id}/create-tasks
GET  /api/workflows/{id}/tasks

GET  /api/agent-runs/{id}
POST /api/agent-runs/{id}/retry
POST /api/agent-runs/{id}/cancel
GET  /api/tasks/{id}
PUT  /api/tasks/{id}/assignee
```

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

测试通过 Testcontainers 启动 PostgreSQL 16，并在空库执行全部 Flyway migration。JPA 使用 `ddl-auto=validate`，数据库结构只由 Flyway 管理。

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
