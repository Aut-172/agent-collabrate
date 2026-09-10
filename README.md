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
- Task Package v1：任务创建后生成不可变 Markdown + JSON 包，保存 SHA-256 哈希并提供当前/历史/差异读取。
- 任务包确认：当前负责人按 package ID、版本和哈希确认后开始开发；重新分配会保留旧包并生成新版本。
- Final Report/TaskDelivery：按 Schema 校验并保存不可变交付记录，强校验当前任务包和确认记录，并固化任务包的 Code Context、Context Plan 和基线 SHA 后异步排队 Git 事实校验。
- Git/CI 同步骨架：GitOperation 保存 Provider 实际验证的 Commit/PR head SHA，CIRun 绑定 TaskDelivery 的同一 Commit SHA；Provider 调用在事务外执行，结果通过独立 Outbox Worker 事务落库并支持退避重试。
- CI Bootstrap 完成门禁：保存 CI 配置存在/Provider 识别证据，当前 SHA 通过后由 Leader 关闭 Workflow，并在同一事务启用项目 CI 门禁。
- 交付事实查询：项目成员可以按 Task 查询历史 GitOperation 和 CIRun，不使用 Final Report 代替 Provider 事实。
- Git Code Context：异步同步默认分支事实并建立版本化 Repo Inventory，过滤敏感路径、二进制和超限文件。
- Context Plan 与取证编排：平台 Agent 基于 Intent + Inventory 选取证据，Orchestrator 按文件数和大小预算读取并保存 Code Context。
- 正式文档上下文门禁：Design、Spec、Build Plan 的 AgentRun 和 DocumentVersion 均绑定 CURRENT Code Context、Context Plan 和 Inventory；默认分支更新会使旧上下文失效。

确定性 Mock Git/CI Provider 只在 `test` 或显式 `mock-provider` Profile 下启用，不能作为生产事实来源。

尚未实现 Blocker、真实 GitHub/Actions Adapter、Webhook、审计通知和管理前端。

## 核心流程

```text
Intent -> Design -> Spec -> Build Plan -> Leader Approval
  Architecture -> Child Intents / Architecture Baseline
  Feature/Change -> Tasks -> TaskAssignments -> Local Agent（后续阶段）

生成请求 -> AgentRun + OutboxJob -> HTTP 202
Worker -> Provider -> Schema 校验 -> DocumentVersion -> Workflow 推进

Git Provider -> Repo Inventory -> Context Planning Agent -> Evidence Orchestrator
  -> Code Context -> Design / Spec / Build Plan Agent
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
POST /api/projects/{id}/code-context/sync
GET  /api/projects/{id}/repo-inventory/latest
GET  /api/projects/{id}/code-context/latest
POST /api/workflows/{id}/code-context/refresh
GET  /api/workflows/{id}/code-context
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
GET  /api/tasks/{id}/packages/current
GET  /api/tasks/{id}/packages/{version}
GET  /api/tasks/{id}/packages/diff?from={from}&to={to}
POST /api/tasks/{id}/packages/{version}/confirm
POST /api/tasks/{id}/delivery
GET  /api/tasks/{id}/deliveries
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
