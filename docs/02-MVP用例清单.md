# MVP 用例清单

## 1. 参与者

| 参与者 | 说明 |
|---|---|
| Leader | 项目治理者 |
| Member | 项目开发成员 |
| System | 平台后台服务 |
| Agent Provider | 平台调用的外部 Agent API |
| Code Context Provider | 为平台 Agent 提供仓库代码事实，可由 Git Provider 或未来 Local Agent Provider 实现 |
| Code Context Orchestrator | 编排 Repo Inventory、Context Plan、多轮取证和上下文版本化 |
| Git Provider | GitHub 等 Git 平台 |
| CI Provider | GitHub Actions 等 CI 平台 |

## 2. 用例总表

| 编号 | 用例 | 主要参与者 | 优先级 |
|---|---|---|---|
| UC-001 | 首次初始化 Leader | System | P0 |
| UC-002 | 登录平台 | Leader/Member | P0 |
| UC-003 | 创建项目 | Leader | P0 |
| UC-003A | 建立项目 CI Bootstrap | Leader/Member/Git Provider/CI Provider | P0 |
| UC-004 | 管理项目成员 | Leader | P0 |
| UC-004A | 填写和更新项目能力画像 | Leader/Member | P0 |
| UC-005 | 创建 Intent | Member/Leader | P0 |
| UC-005A | 同步代码上下文 | System/Code Context Provider/Git Provider | P0 |
| UC-006 | 生成 Design | System/Agent Provider | P0 |
| UC-007 | 编辑和确认 Design | 创建者 | P0 |
| UC-008 | 生成并确认 Spec | System/Agent Provider/创建者 | P0 |
| UC-009 | 生成 Build Plan | System/Agent Provider | P0 |
| UC-010 | 修改和批准 Build Plan | Leader | P0 |
| UC-011 | 创建 Task | Leader/System | P0 |
| UC-012 | 分配和重新分配 Task | Leader | P0 |
| UC-013 | 查看任务看板 | Leader/Member | P0 |
| UC-014 | 下载和确认任务包 | Assignee | P0 |
| UC-015 | 开始本地开发 | Assignee | P0 |
| UC-016 | 报告阻塞 | Assignee | P0 |
| UC-017 | 处理阻塞和更新任务包 | Leader/创建者 | P0 |
| UC-018 | 登记 Git 交付 | Member/Leader | P0 |
| UC-019 | 同步 PR 和 CI | System/Git Provider/CI Provider | P0 |
| UC-020 | 完成 Task | System/Member/Leader | P0 |
| UC-021 | 关闭 Workflow | Leader | P0 |
| UC-022 | 查询审计日志 | Leader/Member | P0 |
| UC-023 | 重试失败的 Agent 或同步任务 | Leader/系统 | P1 |

## 3. 核心用例说明

### UC-004A 填写和更新项目能力画像

参与者：

- Leader；
- Member。

主流程：

1. 用户加入项目；
2. 平台要求填写能力画像；
3. 用户填写职责、技能、经验、偏好和限制；
4. 平台校验必填内容；
5. 保存到该用户在该项目的 `ProjectMember` 关系；
6. 记录画像版本和更新时间；
7. 画像完成后，用户可以成为任务候选人。

规则：

- 画像是项目范围内的数据，不影响用户在其他项目的画像；
- 用户只能修改自己的画像；
- Leader 可以查看项目成员画像，但不能代替成员无痕修改；
- 画像更新不影响已有任务；
- 新的任务分配建议使用最新画像；
- 创建任务时保存画像快照，便于追溯分配依据。

### UC-005 创建 Intent

前置条件：

- 用户已登录；
- 用户是目标项目成员；
- 项目状态为 `ACTIVE`。

主流程：

1. 用户填写标题和自然语言描述；
2. 平台校验字段和项目权限；
3. 创建 Workflow，状态为 `INTENT`；
4. 将创建者加入 `workflow_members`；
5. 写入审计日志；
6. 用户可以发起 Design 生成。

异常：

- 项目不存在或无权限，返回 404 或 403；
- 标题或描述为空，返回 400；
- 项目已归档，返回 409。

### UC-005A 同步代码上下文

前置条件：

- 项目已绑定 Git 仓库；
- 用户是项目成员；
- 项目状态为 `ACTIVE`。

主流程：

1. 用户创建 Intent 或手动点击刷新代码上下文；
2. 平台创建 Code Context 同步任务；
3. Git Provider 读取默认分支、最新 Commit、目录树、文件元数据和白名单文件；
4. 平台保存 Repo Inventory；
5. 平台 Agent 根据 Intent 和 Repo Inventory 生成 Context Plan；
6. Code Context Orchestrator 按计划读取相关文件、Diff 和片段；
7. 平台保存 `CodeContextVersion` 和文件级证据；
8. 当前 Workflow 绑定可用的 Code Context 版本；
9. 后续 AgentRun 使用该版本生成 Design、Spec 或 Build Plan。

异常：

- Git Provider 不可访问，上下文同步任务标记失败或等待重试；
- 仓库权限无效，返回可解释错误；
- 发现默认分支 Commit 已变化，旧 Code Context 标记为 `STALE`；
- Code Context 缺失时，不应静默生成无代码依据的正式 Design。

说明：

- Git Provider 只提供仓库事实，不自主决定哪些文件与 Intent 有关；
- Code Context Provider 和 Orchestrator 不生成最终 Design、Spec 或分工；
- MVP 当前扩展优先实现 Git Provider + Repo Inventory + Context Plan + 受控多轮取证；
- Local Agent Provider 作为未来扩展，只返回 Code Evidence，不接管平台文档生成。

### UC-006 生成 Design

前置条件：

- Workflow 为 `INTENT`；
- 没有同类 `QUEUED/RUNNING` 的 AgentRun。
- 存在可用 Code Context，或 Leader 明确选择有记录的降级生成。

主流程：

1. 用户点击生成；
2. 平台读取 Repo Inventory；
3. 平台 Agent 生成 Context Plan；
4. Code Context Orchestrator 按计划获取 Code Evidence，必要时执行受控补充轮次；
5. 平台绑定 Code Context 版本；
6. 平台创建 `AgentRun` 和 `OutboxJob`；
7. API 返回 202 和 `runId`；
8. Worker 调用 Agent Provider；
9. 保存 Design `DocumentVersion`，记录使用的 Code Context；
10. Workflow 进入 `DESIGN_PROPOSED`；
11. 写入成功审计。

异常：

- Provider 超时，AgentRun 标记 `FAILED`；
- Provider 返回空内容，标记 `FAILED`；
- Code Context 缺失或过期，返回 409 并提示刷新；
- 重复点击，返回已有 `runId` 或 409；
- 外部服务不可用，不改变为成功状态。

### UC-007 编辑和确认 Design

主流程：

1. 创建者查看当前 Design；
2. 修改内容并保存为新版本；
3. 创建者确认指定版本；
4. 平台记录确认人、时间和版本；
5. 允许生成 Spec。

规则：

- 不覆盖旧版本；
- 只有创建者或项目 Leader 可以编辑；
- 已确认版本仍保留历史；
- 重新编辑会使后续 Spec/Plan 视情况失效。

### UC-010 修改和批准 Build Plan

前置条件：

- Workflow 有有效的 `BUILD_PLAN_PROPOSED`；
- Build Plan JSON 通过 Schema 校验。

主流程：

1. Leader 查看 AI 生成的任务和分配建议；
2. Leader 查看 Intent 层级对应的处理管线；
3. Leader 使用表单/表格编辑任务；
4. 系统将修改保存为新的 Plan 版本；
5. Leader 审查任务范围、验收标准、负责人、负责人画像摘要和 Git 策略；
6. 对 Feature/Change，Leader 审查推荐分工模式、推荐人数、当前工作量、容量、匹配理由和警告；
7. 对 Architecture，Leader 审查架构边界、约束和子 Intent，不审查开发分工；
8. Leader 批准具体版本；
9. Workflow 进入 `PLAN_APPROVED`。

异常：

- 负责人不是项目成员，拒绝保存；
- Architecture 包含负责人、推荐人数或 `taskAssignments` 等开发分工字段，拒绝批准；
- Feature/Change 缺少分工建议、人数依据或任务负责人，拒绝批准；
- 任务没有验收标准，拒绝批准；
- JSON Schema 不通过，拒绝保存；
- Spec 已更新，旧 Plan 标记为 `STALE`。
- Code Context 已过期，需要刷新或记录降级原因。

### UC-011 创建 Task

主流程：

1. Leader 从已批准 Plan 发起创建；
2. 平台按任务 key 幂等创建 Task；
3. 为每个 Task 保存来源版本；
4. 生成 Task Package；
5. Workflow 进入 `TASKS_READY`；
6. 写入审计。

不允许：

- 根据未批准 Plan 创建任务；
- Architecture Plan 创建开发 Task 或 TaskAssignment；
- 同一 Plan 重复创建第二组任务；
- 创建没有范围或验收标准的任务。

### UC-014 下载和确认任务包

主流程：

1. 被分配成员打开任务；
2. 平台展示当前 Task Package；
3. 被分配成员复制 Markdown 或下载 Markdown/JSON；
4. 被分配成员在本地仓库中让 Codex CLI 读取任务包；
5. 被分配成员在平台点击“确认此版本并开始开发”；
6. 平台记录 package ID、版本、哈希、确认人和确认时间；
7. 平台校验版本、分配人和项目状态；
8. Task 进入 `IN_PROGRESS`。

异常：

- 任务包不是当前版本，返回 409；
- 任务已重新分配，原负责人不能确认；
- Task 已取消，返回 409；
- Workflow/Project 已归档，返回 409。

### UC-016 报告阻塞

主流程：

1. 本地 Agent 按任务包输出阻塞报告；
2. 被分配成员审阅报告；
3. 被分配成员在平台提交原因、证据和问题；
4. 平台创建 `TaskBlocker`；
5. Task 进入 `BLOCKED`；
6. 任务包保持当前版本但标记为需关注；
7. 通知 Leader 和创建者。

规则：

- 平台无法自动读取成员本地 Agent 状态；
- 只有被分配成员的明确提交才改变平台状态；该成员可以是 Member，也可以是 Leader；
- 不因单个 Task 阻塞而自动阻塞整个 Workflow；
- Workflow 设置 `health = NEEDS_ATTENTION`。

### UC-017 处理阻塞和更新任务包

Leader 可以：

- 回复问题；
- 修改 Spec；
- 修改任务范围；
- 拆分任务；
- 更换负责人；
- 取消任务；
- 标记 Blocker 已解决。

如果修改影响任务范围、验收标准或基础版本：

1. 关闭或解决旧 Blocker；
2. 创建任务/文档新版本；
3. 旧 Task Package 标记 `STALE`；
4. 生成新 Task Package；
5. 通知当前负责人；
6. Member 确认后 Task 恢复 `IN_PROGRESS`。

### UC-018 登记 Git 交付

前置条件：

- Task 已被当前用户分配；
- Member 已在本地完成代码检查；
- 已有分支和 Commit。

主流程：

1. 被分配成员审阅本地 Agent 的 Final Report 和代码 Diff；
2. 被分配成员提交任务包 ID、版本、哈希、branch、Commit SHA、PR URL、交付报告和说明；
3. 平台校验任务包仍为当前版本，且与成员最近确认版本一致；
4. 平台校验交付报告中的任务包版本和哈希；
5. 平台校验任务包版本和交付报告 Schema；
6. 平台查询 Git Provider；
7. 验证仓库、分支、Commit 和 PR；
8. 创建 `TaskDelivery` 和 `GitOperation`；
9. Task 进入 `DELIVERY_SUBMITTED`；
10. 触发或等待 CI 同步。

异常：

- Commit 不存在；
- Commit 不属于目标仓库；
- PR 的 head SHA 不等于提交 SHA；
- 提交的是默认分支；
- 当前任务包过期；
- 任务包 ID、版本或哈希与当前确认记录不一致；
- Final Report 缺失或格式不合法；
- Git Provider 暂时不可访问。

说明：

- Final Report 是本地执行过程的结构化说明；
- Final Report 不能替代平台对 Commit、PR 和 CI 的验证；
- 成员不需要重新上传任务包；平台从当前任务包版本中读取真实内容；
- 任务包更新后，旧版本变为 `STALE`，交付接口返回 `409 TASK_PACKAGE_STALE`；
- 成员点击“提交交付”前必须确认报告内容真实、完整；
- CI 通过后，Task 才能进入 `DONE`。

### UC-019 同步 PR 和 CI

来源：

- Git/CI Webhook；
- 后台定时轮询；
- Leader 手动同步。

主流程：

1. 接收事件或发起查询；
2. 验签、去重、解析外部对象；
3. 以 Commit SHA 关联 Task；
4. 保存 `CIRun`；
5. 更新 Task/Workflow 状态；
6. 写入审计。

规则：

- 旧 Commit 的通过结果不能用于新 Commit；
- 外网失败只能标记 `PENDING/UNKNOWN`；
- CI 通过不是人工输入字段；
- 必要检查集合由项目配置或默认规则决定。

### UC-020 完成 Task

必须满足：

- Task 有当前有效任务包确认记录；
- 有有效 Commit；
- 有关联 PR；
- 当前 Commit 的必要 CI 全部通过；
- 没有未解决 Blocker；
- 没有更晚的 Commit 尚未检查。

完成后：

- Task 进入 `DONE`；
- 生成审计；
- 看板移动到“已完成”；
- 如果所有必要 Task 都完成，Workflow 进入 `READY_TO_CLOSE`。

### UC-021 关闭 Workflow

前置条件：

- Workflow 为 `READY_TO_CLOSE`；
- 所有必要 Task 为 `DONE`；
- 没有未解决 Blocker。

主流程：

1. Leader 查看交付摘要；
2. Leader 执行关闭；
3. Workflow 进入 `DONE`；
4. 写入关闭人、时间和审计。

### UC-003A 初始化工程与 CI

前置条件：

- 项目状态为 `ACTIVE`；
- 项目 `ci_status = CI_NOT_CONFIGURED`；
- 当前没有其他进行中的 `CI_BOOTSTRAP` Workflow。

主流程：

1. Leader 通过专用入口创建“初始化工程与 CI”Workflow；
2. 系统固定设置 `intent_level = FEATURE`、`completion_mode = CI_BOOTSTRAP`；
3. 系统将 Bootstrap 任务直接分配给该 Workflow 的创建者 Leader；
4. 该 Leader 按任务包添加最小工程骨架、构建/测试入口和第一条 CI 配置，并提交 Commit 和 PR；
5. 平台验证 CI 配置存在于当前 Commit；
6. 平台通过 Webhook 或轮询等待 Provider 识别并运行引导检查；
7. 引导检查在当前 Commit 上通过；
8. Leader 确认 Bootstrap 结果；
9. 项目 `ci_status` 从 `CI_NOT_CONFIGURED` 变为 `CI_REQUIRED`；
10. 后续普通 Feature/Change Workflow 使用标准 CI 完成门禁。

异常：

- CI 配置不存在或 Provider 未识别，Bootstrap 保持未完成；
- 引导检查失败，Task 返回开发/返工状态；
- 外部网络不可用，状态为 `PENDING/UNKNOWN`，不能视为通过；
- 不能通过请求参数或人工字段伪造 Bootstrap 成功；
- CI 尚未配置时，普通 Feature/Change 不能直接关闭。

说明：

- Bootstrap 是完成模式，不是新的 Intent 层级；
- Bootstrap 只用于建立初始工程入口和第一条 CI；后续修改 CI 配置使用普通 Change Workflow；
- 一个 Architecture Workflow 可以在没有 CI 的情况下完成，因为它没有代码交付；
- Bootstrap 可以承载项目从零开始的代码骨架和 CI 配置，但不能把普通后续 Feature 当作 Bootstrap 使用；
- 如果 Provider 只在默认分支识别新配置，则使用 Provider 支持的手动触发、分支推送或首次合并后的运行完成验证。

## 4. 权限失败的统一行为

资源不存在和无权访问可统一返回 404，避免泄露资源存在性。已登录但无权执行动作时可返回 403。状态不允许执行时返回 409。
