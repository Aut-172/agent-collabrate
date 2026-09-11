# Vibe Coding 启动指南

## 当前仓库启动

后端和前端已经分别落地。完整本地环境推荐使用 Docker Compose 启动 PostgreSQL、Spring Boot 后端和 Nginx 前端：

```powershell
Copy-Item .env.example .env
# 修改 .env，至少替换 DB_PASSWORD 和 JWT_SECRET 示例值
docker compose up --build -d
docker compose ps
```

前端地址为 `http://localhost:5173`，后端地址为 `http://localhost:8080`。前端容器由 Nginx 提供静态资源并将 `/api` 反向代理到后端。Flyway 在后端容器启动时自动执行，PostgreSQL 数据保存在命名卷中。

`POSTGRES_PASSWORD` 只在空数据卷首次初始化时生效。已有卷在修改 `.env` 的 `DB_PASSWORD` 后如出现密码认证失败，运行 `.\scripts\sync-compose-db-password.ps1` 可保留数据并同步数据库角色密码。只有确认数据可以永久删除时，才使用 `docker compose down --volumes` 重建空卷。

需要直接调试源码时，后端要求 Java 17+、Maven 3.9+ 和 PostgreSQL；前端位于 `frontend/`，使用 Vite + React：

```powershell
# 终端 1：后端
$env:DB_URL = "jdbc:postgresql://localhost:5432/agent_collab"
$env:DB_USERNAME = "agent_collab"
$env:DB_PASSWORD = "change-me"
$env:JWT_SECRET = "replace-with-at-least-32-random-bytes"
mvn spring-boot:run

# 终端 2：前端
cd frontend
npm install
npm run dev
```

Vite 将 `/api` 代理到 `http://localhost:8080`。全量后端测试使用 `mvn test`，需要 Docker Desktop 供 Testcontainers 启动 PostgreSQL。生产凭证只能通过环境变量或密钥管理服务提供，不能打入镜像。

## 1. 是否建议新开会话

建议为正式开发新开一个 Codex 会话或任务。

原因：

- 当前会话主要用于需求澄清和架构决策；
- 新会话可以把文档集作为明确的开发基线；
- 可以减少“讨论中的候选方案”和“已经冻结的方案”混在一起；
- 便于后续把实现、测试和代码审查留在开发任务中。

不需要复制全部历史对话。新会话与当前工作区共享文件时，只需要让它读取 `docs/` 并以文档为准。

## 2. 推荐启动 Prompt

将下面的内容作为新开发会话的第一条 Prompt：

```text
你是这个仓库的主开发 Agent。请在开始编码前先完整阅读并遵守以下文档：

必读文档：
1. docs/README.md
2. docs/01-MVP需求分析与PRD.md
3. docs/02-MVP用例清单.md
4. docs/03-MVP业务流程与状态机.md
5. docs/04-MVP技术方案.md
6. docs/05-任务包与本地Agent交接规范.md
7. docs/06-Git-PR-CI集成与网络可靠性.md
8. docs/07-Leader操作与实体影响矩阵.md
9. docs/08-MVP验收测试矩阵.md
10. docs/09-架构决策记录.md
11. docs/10-数据库表设计.md
12. docs/12-MVP前端设计规范与页面说明.md
13. docs/13-任务包确认与交付流程补充.md
14. docs/14-Intent层级与AI分工策略.md
15. docs/15-CodeContextProvider与代码上下文机制.md

开发约束：
- 以这些文档中的“已冻结的 MVP 决策”和 ADR 为准；
- 不要自行引入文档中明确排除的能力；
- 不要把成员能力画像放到 users，能力画像必须属于 project_members；
- Leader 既是项目治理者，也是可被 Agent 分配开发任务的项目成员；
- Agent 分配建议必须读取当前项目成员画像；
- 平台 Agent 生成 Design、Spec 和 Build Plan 前，必须通过 Code Context Provider 获取可追溯代码事实；
- MVP 当前扩展优先实现 Git Provider + Repo Inventory + Context Plan + Code Context Orchestrator；
- Git Provider 只读取仓库事实，不自主判断哪些文件与 Intent 有关；
- 平台 Agent 先根据 Intent 和 Repo Inventory 生成 Context Plan，再由 Orchestrator 多轮、受控地读取文件和 Diff；
- LocalAgentCodeContextProvider 只作为未来扩展预留，不要在当前阶段让平台保存成员本地 Agent Key 或直接远程控制本地 Codex；
- 本地 Agent 可以提供未来的 Code Evidence，但正式 Design/Spec/Plan 仍由平台 Agent 生成、校验、版本化和审批；
- Architecture 只产生架构基线和子 Intent 建议，不产生开发分工；
- Feature/Change 必须产生 AI 分工建议；Feature 通常比 Change 推荐更多成员，但人数是软规则，必须结合范围、依赖、画像和当前工作量解释；
- 新项目默认 `ci_status = CI_NOT_CONFIGURED`；必须由 Leader 通过一次性的 `FEATURE + CI_BOOTSTRAP` 建立最小工程骨架、构建测试入口并验证第一条真实 CI，成功后项目才进入 `CI_REQUIRED`；
- `CI_BOOTSTRAP` 不等于永久免 CI，不能用人工字段、Final Report 或空流水线伪造通过；
- task_assignments 必须保存分配时的画像版本、画像快照和分配理由；
- task_assignments 还必须保存工作量快照和分配评分；
- 本地 Agent 完成任务后必须输出结构化 Final Report，成员审阅后才能提交 TaskDelivery；
- Final Report 是交接和审计信息，不替代 Git Provider 与 CI Provider 的事实校验；
- 平台只负责任务编排、代码上下文获取和平台侧 Agent 文档生成；
- 平台不访问成员本地代码，不在服务器执行成员代码；
- 本地 Agent 通过任务包执行代码修改、测试和允许的 Git 操作；
- 文档、任务、任务包、分配和审计记录不能静默覆盖历史；
- CI 结果必须绑定当前 Commit SHA，不能用旧 Commit 的通过结果完成任务；
- 不要把任何 API Key、Token 或密码写入代码、任务包、日志或测试输出。

开始步骤：
1. 先检查工作区、现有文件和 git 状态；
2. 阅读上述文档并总结当前实现基线；
3. 找出文档要求与现有代码之间的差距；
4. 提出一个分阶段实现计划，先实现基础骨架、数据库和认证；
5. 在我确认计划后再开始大范围编码；
6. 每完成一个阶段都运行对应测试并报告结果；
7. 如果文档之间发现矛盾，暂停编码，指出具体文件和冲突，不要自行猜测。

实现偏好：
- 优先使用项目已有技术和代码风格；
- 使用 Flyway，不使用 ddl-auto=update；
- 使用 PostgreSQL 作为主要集成测试数据库；
- 使用 DTO、Service、Repository、Client 分层；
- 状态转换集中在 Service/Domain 层，Controller 不直接修改状态；
- 外部 Agent、Git 和 CI 调用必须异步化并保留运行记录；
- 先实现可测试的后端 API，再实现最小管理界面；
- 添加能覆盖权限、状态机、版本过期、画像分配、Git/CI SHA 绑定的测试。

现在先不要写代码。先完成工作区检查、文档阅读和实现差距分析，然后给出计划。
```

## 3. 如果希望直接开始编码

如果不想分两轮确认，可以把 Prompt 的最后一段改成：

```text
完成工作区检查和文档阅读后，直接开始实现第一阶段：项目骨架、PostgreSQL/Flyway、User、Project、ProjectMember、能力画像、登录和基础权限。保持小步推进，每完成一组测试就报告结果。
```

## 4. 推荐开发顺序

1. 项目骨架、配置、异常处理；
2. Flyway、User、Project、ProjectMember；
3. 项目能力画像和画像版本；
4. 登录、JWT 和项目级权限；
5. Workflow、DocumentVersion 和状态机；
6. Code Context Provider、Repo Inventory、Context Plan 和 Mock/Git 实现；
7. AgentRun、OutboxJob 和 Mock Agent Provider；
8. Build Plan Schema、Task、TaskAssignment；
9. TaskPackage、版本确认和 Blocker；
10. 看板和通知；
11. Git/PR/CI 同步；
12. Webhook、审计、错误恢复和 Vite + React 最小前端（当前已完成）；
13. 后续阶段补充更完整的管理交互和验收覆盖。

## 5. 开发会话的停止条件

开发 Agent 不应在以下情况下自行做大范围决策：

- 需求文档和技术方案冲突；
- 需要新增未定义的业务状态；
- 需要改变 Leader/Member 权限模型；
- 需要把平台改成远程代码执行平台；
- 需要保存成员本地 Agent Key；
- 需要绕过当前 Commit 的 CI 校验；
- 需要把普通 Feature/Change 标记成 `CI_BOOTSTRAP` 以绕过 CI；
- 需要删除或覆盖历史文档、任务包或审计记录。

遇到这些情况，应先列出冲突和建议，等待确认。
