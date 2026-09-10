# AI Agent 协作开发平台

## MVP 技术方案

## 1. 架构边界

### 1.1 平台侧

平台服务端负责：

- 用户、项目和成员管理；
- Workflow、DocumentVersion、Task 和 TaskPackage 持久化；
- 调用外部 Agent API 生成 Design、Spec、Build Plan；
- 保存 AgentRun 和审计日志；
- 生成任务和任务包；
- 查询、校验和同步 Git、PR、CI；
- 提供看板和通知查询。

### 1.2 成员本地侧

成员本地负责：

- Clone/Checkout 仓库；
- 读取任务包；
- 使用 Codex CLI 或其他 Agent 修改代码；
- 运行测试和检查 Diff；
- 按任务包执行分支、Commit、Push 和可选 PR 创建；
- 回平台登记交付；
- 主动报告阻塞。

平台不访问成员本地文件，不执行成员代码，不保存成员本地 Agent Key。

## 2. 总体架构

```text
┌──────────────────────────────┐
│ 成员本地环境                  │
│ Git 仓库 + Codex CLI          │
│ 本地测试 + Commit + Push      │
└──────────────┬───────────────┘
               │ branch/commit/PR/CI metadata
┌──────────────▼─────────────────────────────────────────┐
│ Spring Boot 单体应用                                    │
│                                                        │
│ REST Controller                                         │
│   -> Application Service                               │
│      -> Domain State Machine                           │
│      -> Repository                                     │
│      -> Outbox Worker                                  │
│                                                        │
│ Agent Provider Client / Git Client / CI Client          │
└──────────┬────────────────────┬────────────────────────┘
           │                    │
      PostgreSQL           外部 Provider API
```

## 3. 技术栈

| 类别 | 技术 | 约束 |
|---|---|---|
| 语言 | Java 17+ | 使用 Spring Boot 3.x |
| Web | Spring MVC 或 WebFlux | MVP 推荐 Spring MVC + WebClient |
| 数据访问 | Spring Data JPA / Hibernate | 事务边界放在 Service |
| 数据库 | PostgreSQL | 不以 H2 作为主要兼容性依据 |
| 迁移 | Flyway | `ddl-auto=validate` |
| 认证 | Spring Security + JWT | 无状态 API |
| HTTP | WebClient | 外部调用设置超时和重试 |
| 异步 | OutboxJob + 定时 Worker | MVP 不引入消息队列 |
| 校验 | Jakarta Bean Validation + JSON Schema | 校验请求和 Build Plan |
| 测试 | JUnit 5、Mockito、Testcontainers | 集成测试使用 PostgreSQL |
| 文档 | springdoc-openapi | 生成 API 文档 |
| Git | GitHub REST API 优先 | 不使用 JGit 修改仓库 |

平台侧的 Agent Provider 适配器可使用 OpenAI Responses API 或其他供应商接口。模型 ID 和供应商 URL 必须配置化，不能写死为某个历史模型名。参考：[OpenAI Responses API](https://developers.openai.com/api/reference/cli/resources/responses/methods/create)。

## 4. 模块结构

```text
com.example.agentcollab
├── config
├── controller
├── dto
├── entity
├── repository
├── security
├── client
│   ├── AgentProviderClient
│   ├── GitProviderClient
│   └── CiProviderClient
├── service
│   ├── auth
│   ├── project
│   ├── workflow
│   ├── document
│   ├── plan
│   ├── task
│   ├── taskpackage
│   ├── blocker
│   ├── agent
│   ├── git
│   ├── ci
│   ├── board
│   ├── audit
│   └── job
└── exception
```

## 5. 领域实体

### 5.1 User

保存登录身份和全局启用状态。User 不保存项目角色和项目能力画像。

### 5.2 Project / ProjectMember

Project 保存仓库、默认分支、Git Provider 和 CI 生命周期状态。ProjectMember 保存用户在项目中的 `LEADER/MEMBER` 角色，以及该成员自填的项目能力和职责画像。

项目 `ci_status` 取值：

```text
CI_NOT_CONFIGURED
CI_REQUIRED
```

新项目默认为 `CI_NOT_CONFIGURED`。第一个负责建立 CI 的 Workflow 使用 `completion_mode = CI_BOOTSTRAP`，验证通过后切换为 `CI_REQUIRED`。

Leader 与 Member 都是开发任务候选人。Leader 的额外权限只来自 `project_role`，不代表 Leader 不参与开发。

建议能力画像使用 JSONB，而不是一段无法校验的自由文本：

```json
{
  "summary": "负责后端服务和认证模块",
  "responsibilities": ["后端架构", "接口开发", "代码评审"],
  "skills": ["Java", "Spring Boot", "PostgreSQL"],
  "experience": ["认证系统", "REST API"],
  "preferredTaskTypes": ["后端开发", "数据库设计"],
  "limitations": ["不负责前端视觉设计"],
  "availability": "PART_TIME",
  "weeklyCapacityPoints": 13,
  "notes": "每周可投入约 15 小时"
}
```

### 5.3 Workflow / WorkflowMember

Workflow 保存 Intent、项目、`intent_level`、`completion_mode`、可选父 Workflow 和状态；WorkflowMember 保存 OWNER/PARTICIPANT 关系。

`intent_level` 取值为 `ARCHITECTURE`、`FEATURE`、`CHANGE`。Architecture、Feature 和 Change 使用不同的 Agent 输出 Schema 和后续处理管线，但共享 Workflow 生命周期状态机。

`completion_mode` 取值为 `ARCHITECTURE_BASELINE`、`CI_BOOTSTRAP`、`CI_REQUIRED`。它与 Intent 层级正交，不能把 CI Bootstrap 伪装成新的 Intent 层级。

默认映射规则：

- `ARCHITECTURE` 只能使用 `ARCHITECTURE_BASELINE`；
- `CI_NOT_CONFIGURED` 项目只能有一个进行中的 `CI_BOOTSTRAP`；
- `CI_REQUIRED` 项目的普通 Feature/Change 使用 `CI_REQUIRED`；
- 未完成 Bootstrap 时，普通 Feature/Change 可以保存为需求，但不能创建绕过 CI 的可关闭开发任务。

### 5.4 DocumentVersion

保存 Design、Spec、Build Plan 的不可变版本，关联来源 AgentRun 或人工修改者。

### 5.5 Task / TaskAssignment

Task 保存可交付开发单元和 `effort_points`。Architecture 不创建开发 Task；Feature/Change 的 Task 必须来源于已批准的分工建议。

TaskAssignment 保存分配历史、Agent 分配理由、分配评分、分配时的能力画像快照、工作量快照和确认时间，不能只覆盖当前 assignee。

Agent 生成的分配建议至少包含：

```json
{
  "userId": 12,
  "projectRole": "LEADER",
  "profileVersion": 2,
  "reason": "熟悉 Spring Security 和认证系统",
  "confidence": 0.86,
  "workloadSnapshot": {
    "openEffortPoints": 5,
    "weeklyCapacityPoints": 13
  },
  "assignmentScore": 0.91
}
```

该建议必须基于当前项目成员的能力画像。Leader 可以修改建议；批准和创建任务时，系统必须重新校验负责人仍是 ACTIVE 项目成员且画像已完成。

分工建议必须包含 `staffingRecommendation.mode`、`recommendedTeamSize`、人数理由、候选成员匹配理由和警告。Feature 通常应比 Change 推荐更大的团队，但 AI 可以根据实际范围给出单人 Feature 或多人 Change，并必须说明原因。Architecture 输出不得包含开发分工字段。

### 5.6 TaskPackage

保存任务交接用的 Markdown、JSON、版本、哈希和来源文档版本。

### 5.7 TaskDelivery

保存一次本地开发交付尝试，包括 Final Report、任务包版本、分支、Commit、PR 和提交人。一个 Task 可以有多次交付尝试，用于支持 CI 失败后的返工。

### 5.8 TaskBlocker

保存阻塞原因、证据、问题、处理人和解决记录。

### 5.9 AgentRun

保存一次平台侧 Agent 调用的类型、模型、状态、摘要、错误和重试信息。

### 5.10 GitOperation / CIRun

分别保存分支、Commit、PR 元数据和某个 Commit 对应的 CI 运行。

### 5.11 AuditLog / Notification / OutboxJob

- AuditLog：不可篡改的操作记录；
- Notification：任务包更新、阻塞和失败提醒；
- OutboxJob：异步 Agent、Git、CI 任务。

### 5.12 CI Bootstrap 验证

CI Bootstrap 不是“Leader 手工勾选 CI 已通过”，而是一个受限的完成模式。系统至少验证：

```text
bootstrapCommit 存在且属于目标仓库
AND CI 配置存在于 bootstrapCommit
AND Provider 已识别 CI 配置
AND bootstrapCommit 的引导检查为 PASSED
AND 没有未解决 Blocker
```

验证通过后，系统在事务中将项目 `ci_status` 更新为 `CI_REQUIRED`，并记录 AuditLog。Provider 网络故障只能得到 `PENDING/UNKNOWN`，不能推进项目状态。

## 6. 关键数据库设计

建议表：

```text
users
projects
project_members
workflows
workflow_members
document_versions
tasks
task_assignments
member_profile_versions
task_packages
task_package_confirmations
task_deliveries
task_blockers
agent_runs
git_operations
ci_runs
audit_logs
notifications
outbox_jobs
webhook_deliveries
```

关键约束：

- `project_members(project_id, user_id)` 唯一；
- `project_members.capability_profile` 必须通过 JSON Schema 校验；
- `project_members.weekly_capacity_points` 和 `availability` 用于当前分工评估；
- 项目内所有可分配成员都必须有已完成能力画像；
- `task_assignments` 保存 `profile_snapshot`，不随成员后续修改而变化；
- `task_assignments` 保存 `workload_snapshot` 和 `assignment_score`；
- `tasks.effort_points` 必须在 1-8 范围内；
- `document_versions(workflow_id, document_type, version_no)` 唯一；
- `task_packages(task_id, version)` 唯一；
- `task_package_confirmations(task_id, user_id, package_version)` 唯一；
- `webhook_deliveries(provider, delivery_id)` 唯一；
- Task 和 Workflow 使用乐观锁；
- 审计日志不提供修改和删除 API。

## 7. API 设计

统一前缀：`/api`。长耗时动作返回 `202 Accepted` 和运行记录地址。

### 7.1 认证和项目

```text
POST /api/auth/login
GET  /api/me
POST /api/users
POST /api/projects
GET  /api/projects
GET  /api/projects/{id}
POST /api/projects/{id}/members
DELETE /api/projects/{id}/members/{userId}
GET  /api/projects/{id}/members
GET  /api/projects/{id}/members/me/profile
PUT  /api/projects/{id}/members/me/profile
POST /api/projects/{id}/ci-bootstrap
```

### 7.2 Workflow 和文档

```text
POST /api/projects/{projectId}/workflows
GET  /api/workflows
GET  /api/workflows/{id}
POST /api/workflows/{id}/generate-design
PUT  /api/workflows/{id}/design
POST /api/workflows/{id}/confirm-design
POST /api/workflows/{id}/generate-spec
PUT  /api/workflows/{id}/spec
POST /api/workflows/{id}/confirm-spec
POST /api/workflows/{id}/generate-build-plan
PUT  /api/workflows/{id}/plan-drafts
POST /api/workflows/{id}/approve-plan
POST /api/workflows/{id}/create-tasks
POST /api/workflows/{id}/close
POST /api/workflows/{id}/cancel
```

`generate-build-plan` 根据 `intent_level` 选择输出：

- Architecture：架构设计、约束、非功能需求和子 Intent 建议，不输出开发分工；
- Feature：功能 Build Plan 和 AI 分工建议；
- Change：局部变更计划和 AI 分工建议。

`approve-plan` 必须校验输出 Schema 与 Intent 层级一致。`create-tasks` 对 Architecture 只能创建子 Intent，不得创建开发 Task。

`POST /api/projects/{id}/ci-bootstrap` 只能由 Leader 在 `ci_status = CI_NOT_CONFIGURED` 时调用。接口创建或登记一个 `CI_BOOTSTRAP` Workflow，并确保项目内同时只有一个进行中的 Bootstrap。也可以由普通 Workflow 创建流程显式声明 `completion_mode = CI_BOOTSTRAP`，但服务端必须执行相同的唯一性和权限校验。

项目创建时不接受客户端直接设置 `ci_status`；服务端固定初始化为 `CI_NOT_CONFIGURED`。`ci_status` 只能由 Bootstrap 成功关闭这一条业务路径更新为 `CI_REQUIRED`。

### 7.3 AgentRun

```text
GET  /api/agent-runs/{id}
POST /api/agent-runs/{id}/retry
POST /api/agent-runs/{id}/cancel
```

### 7.4 Task、任务包和阻塞

```text
GET  /api/workflows/{workflowId}/tasks
GET  /api/workflows/{workflowId}/board
GET  /api/tasks/{id}
PUT  /api/tasks/{id}/assignee
GET  /api/tasks/{id}/packages/current
GET  /api/tasks/{id}/packages/{version}
GET  /api/tasks/{id}/packages/diff?from={from}&to={to}
POST /api/tasks/{id}/packages/{version}/confirm
POST /api/tasks/{id}/block
POST /api/tasks/{id}/blockers/{blockerId}/resolve
POST /api/tasks/{id}/delivery
POST /api/tasks/{id}/complete
POST /api/tasks/{id}/cancel
```

### 7.5 Git、CI 和审计

```text
POST /api/tasks/{id}/git-operations
POST /api/tasks/{id}/pull-request
GET  /api/tasks/{id}/deliveries
GET  /api/tasks/{id}/git-operations
GET  /api/tasks/{id}/ci-runs
POST /api/projects/{id}/git-sync
POST /api/projects/{id}/ci-sync
POST /api/webhooks/git/{provider}
GET  /api/audit-logs
GET  /api/workflows/{id}/audit-logs
GET  /api/notifications
POST /api/notifications/{id}/read
```

## 8. 异步处理

### 8.1 创建任务

业务请求在一个事务中：

```text
校验权限和状态
  -> 创建 AgentRun = QUEUED
  -> 创建 OutboxJob = PENDING
  -> 写入审计
  -> 返回 runId
```

### 8.2 Worker

Worker 使用数据库锁获取任务：

1. 查询到期的 `PENDING` Job；
2. 使用行锁或 `SKIP LOCKED` 抢占；
3. 标记 `RUNNING`；
4. 调用外部 Client；
5. 保存结果；
6. 成功标记 `SUCCEEDED`，失败记录错误并安排重试；
7. 超过次数后标记 `FAILED`；
8. 回收超时的 `RUNNING` Job。

### 8.3 重试

- 连接失败、超时和临时 5xx 可重试；
- 参数错误和权限错误不自动重试；
- 默认最多自动重试一次；
- 人工重试创建新的运行记录或明确关联旧记录；
- 幂等键避免重复创建业务结果。

## 9. 权限模型

每次资源访问按以下顺序校验：

```text
认证 -> 项目存在 -> 项目成员关系 -> 项目角色 -> 资源归属 -> 状态规则
```

Leader 的治理权限来自 `project_members.project_role`。Leader 和 Member 都可以成为任务负责人；任务执行权限来自当前 `TaskAssignment`，不来自全局角色。Member 不得修改其他项目资源，即使知道资源 ID。

## 10. 配置与部署

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/agent_collab
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true

app:
  jwt:
    secret: ${JWT_SECRET}
    access-token-expiration: 3600000

agent:
  provider: ${AGENT_PROVIDER:openai}
  api-url: ${AGENT_API_URL}
  api-key: ${AGENT_API_KEY}
  model: ${AGENT_MODEL}
  timeout-seconds: 120
  max-retries: 1

git:
  provider: ${GIT_PROVIDER:github}
  api-url: ${GIT_API_URL:https://api.github.com}
  token: ${GIT_TOKEN}

ci:
  webhook-secret: ${CI_WEBHOOK_SECRET}
  sync-interval-seconds: 30

jobs:
  poll-interval-seconds: 5
  max-concurrency: 4
```

生产环境使用 Docker、HTTPS、PostgreSQL、Flyway、环境变量或密钥管理服务。数据库定期备份。

## 11. 测试策略

### 单元测试

- 状态机；
- 权限；
- Plan JSON Schema；
- 版本失效；
- 幂等；
- CI 完成条件；
- 阻塞恢复；
- 审计生成。

### 集成测试

- Flyway migration；
- PostgreSQL 数据隔离；
- Mock Agent Provider；
- Mock Git/CI Provider；
- Webhook 验签和去重；
- Outbox Worker 重试；
- 当前 SHA 与 CI SHA 匹配。

## 12. 开发顺序

1. 项目骨架、数据库和认证；
2. Project、ProjectMember、能力画像和画像版本；
3. 文档版本和状态机；
4. AgentRun、OutboxJob 和 Mock Provider；
5. Plan Schema、Task、TaskAssignment；
6. TaskPackage、Blocker 和看板；
7. Git/PR/CI 同步；
8. 审计、通知和错误恢复；
9. 最小前端和完整验收测试。
