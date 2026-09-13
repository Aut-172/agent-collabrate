# MVP 验收测试矩阵

## 1. 验收原则

- 每条 P0 需求至少有一个可执行验证；
- 状态、权限、版本和外部事实优先使用自动化测试；
- 外部 Provider 使用 Mock，不依赖真实网络；
- 测试必须验证失败路径，不能只验证主流程；
- 当前 Commit 和 CI SHA 的一致性是完成判断的强制条件。

## 2. 产品验收矩阵

| 编号 | 验收条件 | 验证方式 |
|---|---|---|
| AT-001 | 首个 Leader 可以初始化 | 集成测试/部署测试 |
| AT-002 | Member 不能自行注册为 Leader | 权限测试 |
| AT-003 | Leader 可以创建项目并添加成员 | API 集成测试 |
| AT-004 | Member 只能访问所属项目 | 越权测试 |
| AT-004A | 成员加入项目后必须填写能力画像 | API/业务规则测试 |
| AT-004B | Leader 也必须填写能力画像并可成为任务负责人 | 任务分配集成测试 |
| AT-004C | Agent 分配建议使用项目成员画像 | Agent Prompt/Plan 测试 |
| AT-004H | Code Context Provider 可以同步仓库 Commit、目录树、文件元数据和白名单文件 | Mock/Git Client 测试 |
| AT-004I | Code Context 缺失或过期时不能静默生成正式 Design | API/状态测试 |
| AT-004J | AgentRun 和生成文档记录使用的 Code Context 版本 | Repository/集成测试 |
| AT-004K | 平台 Agent 先生成 Context Plan，再由 Orchestrator 定向读取文件证据 | Worker/服务测试 |
| AT-004L | Git Provider 不包含自主相关性判断，只按请求返回仓库事实 | 单元测试/接口测试 |
| AT-004M | Code Context 多轮补充受轮次、文件数和大小预算限制 | Worker/配置测试 |
| AT-004G | 新项目默认处于 `CI_NOT_CONFIGURED` | 数据库/项目创建测试 |
| AT-004D | Architecture 不生成开发分工、负责人或 TaskAssignment | Schema/状态机测试 |
| AT-004E | Feature/Change 生成分工模式、推荐人数、理由、工作量依据和警告 | Agent 输出/Schema 测试 |
| AT-004F | Feature 默认倾向于比 Change 推荐更多成员，但简单 Feature 和复杂 Change 可由 AI 例外裁定并说明理由 | 规则测试 |
| AT-005 | Member 可以创建 Intent | API 测试 |
| AT-006 | Design 生成是异步的并返回 runId | API + Worker 测试 |
| AT-007 | Agent 失败可查看原因并重试 | Worker 集成测试 |
| AT-008 | Design、Spec、Plan 历史版本不被覆盖 | Repository 测试 |
| AT-009 | Spec 未确认不能生成有效 Plan | 状态机测试 |
| AT-010 | Build Plan JSON Schema 不通过时不能批准 | Schema/API 测试 |
| AT-011 | Agent 分配建议可以被 Leader 修改 | Service 测试 |
| AT-011A | 分配记录保存画像快照和分配理由 | Repository/集成测试 |
| AT-011B | 分配记录保存工作量快照、分配评分和画像版本 | Repository/集成测试 |
| AT-011C | 已开始执行的任务不因工作量变化自动换人 | 状态机/服务测试 |
| AT-012 | 未批准 Plan 不能创建 Task | 权限/状态测试 |
| AT-013 | 创建 Task 具有来源版本信息 | 集成测试 |
| AT-014 | Task Package 同时有 Markdown 和 JSON | API 测试 |
| AT-015 | Task Package 包含 baseCommit 和版本信息 | JSON Schema 测试 |
| AT-015A | Task Package 包含 codeContextVersionId 和相关文件证据摘要 | JSON Schema 测试 |
| AT-016 | 任务包更新后旧版本标记 STALE | Service 测试 |
| AT-017 | 过期任务包不能开始开发 | 409 API 测试 |
| AT-018 | 过期任务包不能提交交付 | 409 API 测试 |
| AT-019 | 看板按 Task 状态正确聚合 | 查询测试 |
| AT-019A | 看板保留 `CANCELLED` 和 `FAILED` 终态任务，跨项目访问被拒绝 | 查询/权限测试 |
| AT-020 | Member 只能开始自己负责的 Task | 权限测试 |
| AT-021 | Member 可以提交结构化 Blocker | API 集成测试 |
| AT-022 | Blocker 后 Workflow 不无条件整体阻塞 | 状态机测试 |
| AT-023 | Leader 解决 Blocker 后生成新任务包 | 集成测试 |
| AT-024 | Leader 可以重新分配未完成 Task | Service/API 测试 |
| AT-025 | 重新分配会使旧任务包失效 | Service 测试 |
| AT-026 | Commit 不存在时拒绝交付 | Git Client Mock 测试 |
| AT-027 | 跨仓库 Commit 被拒绝 | Git Client Mock 测试 |
| AT-028 | PR head SHA 不匹配时拒绝交付 | Git Client Mock 测试 |
| AT-028A | Final Report 缺失或 Schema 不合法时拒绝交付 | API/Schema 测试 |
| AT-028B | TaskDelivery 保存任务包版本和报告内容 | 集成测试 |
| AT-028C | Final Report 中声称测试通过但 CI 未通过时不能完成任务 | 状态机测试 |
| AT-029 | Webhook 签名错误不改变业务状态 | Webhook 测试 |
| AT-030 | 重复 Webhook 不重复创建 CIRun | 幂等测试 |
| AT-031 | Git API 不可达时不能误判成功 | 故障测试 |
| AT-032 | CI 运行中不能完成 Task | 状态机测试 |
| AT-033 | CI 通过但对应旧 Commit 时不能完成 Task | 关键规则测试 |
| AT-034 | 当前 Commit 的必要 CI 通过后可以完成 Task | 集成测试 |
| AT-035 | 所有必要 Task 完成后 Workflow 才能关闭 | 状态机测试 |
| AT-036 | 未解决 Blocker 时不能关闭 Workflow | 状态机测试 |
| AT-037 | Leader 不能手工伪造 CI PASSED | 权限/接口测试 |
| AT-037A | CI 未配置时普通 Feature/Change 不能直接关闭 | 状态机/API 测试 |
| AT-037B | 工程与 CI 初始化可在空仓库或无初始 CI 时开始，但必须验证当前 Bootstrap Commit 的真实 CI 检查 | Git/CI 集成测试 |
| AT-037C | Bootstrap 成功后项目切换为 `CI_REQUIRED` 且不能重复绕过门禁 | 事务/状态机测试 |
| AT-037D | 空流水线或 Final Report 声称通过不能完成 Bootstrap | Git/CI 事实校验测试 |
| AT-037E | 工程与 CI 初始化只能由 Leader 通过专用入口创建，固定为 Feature，且任务初始分配给创建该 Workflow 的 Leader | 权限/任务分配集成测试 |
| AT-037F | CI 已初始化后的配置修改走普通 Change Workflow，不能再次创建 Bootstrap | API/状态机测试 |
| AT-038 | 关键动作均有 AuditLog | 审计集成测试 |
| AT-039 | 审计日志不可修改和删除 | API 测试 |
| AT-040 | 敏感凭证不进入日志和响应 | 安全测试 |
| AT-041 | 通知只对接收人可见，且只有接收人可幂等标记已读 | API/权限测试 |
| AT-042 | 初始分配、重新分配和任务包更新通知当前负责人 | 服务/集成测试 |
| AT-043 | Blocker 创建通知 Leader 与 Workflow 创建者并去重，关闭后通知当前负责人 | 服务/集成测试 |

当前回归基线：后端 Maven 测试共 49 项，其中非容器测试通过；3 个 PostgreSQL Testcontainers 集成测试需要可用 Docker Engine，Docker 不可用时应明确记为环境阻塞而不是代码失败。前端 Vitest 18 项通过，`npm run build` 通过。Flyway 当前迁移为 V1-V22。已覆盖实现包括看板、通知、Webhook、审计、Profile 隔离、任务包交接、Blocker、Final Report 和 Git/CI 证据页面。

## 3. 关键状态机测试

### 正向流程

```text
INTENT
 -> DESIGN_PROPOSED
 -> DESIGN_CONFIRMED
 -> SPEC_PROPOSED
 -> SPEC_CONFIRMED
 -> BUILD_PLAN_PROPOSED
 -> PLAN_APPROVED
 -> TASKS_READY
 -> IN_PROGRESS
 -> DELIVERY_SUBMITTED
 -> CI_RUNNING
 -> CI_PASSED
 -> READY_TO_CLOSE
 -> DONE
```

### 必须拒绝的流程

- `INTENT -> DONE`
- `DESIGN_PROPOSED -> PLAN_APPROVED`
- 确认 Design 后状态必须为 `DESIGN_CONFIRMED`；生成 Spec 后才进入 `SPEC_PROPOSED`；
- 未确认 Spec 直接创建 Task；
- 未批准 Plan 创建 Task；
- CI `FAILED -> DONE`；
- 旧 SHA 的 CI `PASSED -> DONE`；
- 有 Open Blocker 时 `READY_TO_CLOSE`；
- 非负责人开始或提交 Task；
- 未完成能力画像的成员被分配 Task；
- 非 Leader 批准 Plan；
- 被移除项目成员继续写入资源。

## 4. 外部服务故障测试

模拟：

- Agent 超时；
- Agent 429；
- Agent 5xx；
- Code Context Provider 同步失败；
- Code Context 过期；
- Context Plan 无法找到足够证据；
- 多轮取证达到预算上限；
- Git API DNS/连接失败；
- Git API 限流；
- CI Webhook 延迟；
- Webhook 重复；
- Webhook 签名错误；
- CI 结果缺失；
- Commit 已被新 SHA 替换。

验证：

- 任务不被误标成功；
- 运行记录保留错误；
- 后台任务可重试；
- 页面展示可解释状态；
- 手动同步有效；
- 旧结果不污染新交付。
- 无代码上下文时不会生成看似正常的 Design。

## 5. 安全测试

- 未登录访问受保护接口返回 401；
- Member 访问其他项目返回 404/403；
- Member 调用 Leader 接口返回 403；
- 客户端传入 role 不会提升权限；
- JWT Secret 不出现在响应和日志；
- Git Token 不出现在任务包；
- Code Context 不采集密钥文件、二进制文件和超限大文件；
- Webhook 必须验签；
- SQL/JSON 输入不能绕过资源条件；
- 并发更新使用乐观锁；
- 重复请求不会重复创建任务或任务包。

补充验收：

- Workflow 创建时默认 pull_request_required = TRUE；Architecture 固定为不适用；
- pull_request_required = TRUE 时没有 PR 的交付返回 PULL_REQUEST_REQUIRED；
- pull_request_required = FALSE 时允许无 PR，但仍校验任务分支、Commit HEAD 和 CI；
- TaskPackage 的 executionPolicy.git.createPullRequest 与 Workflow 策略一致。
- 所有项目成员都可以请求 Code Context/Repo Inventory 同步；非成员仍被拒绝；
- 每次 Workflow 创建都会排队一次 Repo Inventory 刷新，刷新期间旧 Inventory/Code Context 不得继续作为当前事实生成 Context Plan；
- 远程默认分支 Commit 变化并完成同步后，旧 Repo Inventory 标记为 `STALE`，新 Commit 对应版本标记为 `CURRENT`。

## 6. Definition of Done

MVP 代码可以进入交付阶段前必须满足：

1. P0 验收条目全部通过；
2. Flyway migration 可以在空数据库执行；
3. PostgreSQL 集成测试通过；
4. 外部 Provider 都有 Mock 测试；
5. OpenAPI 文档与实现一致；
6. 关键状态转换有单元测试；
7. 权限和跨项目隔离有集成测试；
8. CI SHA 绑定规则有测试；
9. 无未解决的高风险安全问题；
10. 文档中的已冻结决策与实现一致。
