# Leader 操作与实体影响矩阵

## 1. 权限前提

Leader 权限以 `project_members.project_role = LEADER` 为准。每项操作都必须先校验项目范围，不能因为用户是某个项目的 Leader 就访问其他项目。

## 2. 项目操作

| Leader 操作 | 直接影响实体 | 具体影响 | 限制 |
|---|---|---|---|
| 创建项目 | Project、ProjectMember | 创建项目并自动加入 Leader | 项目名和仓库配置必填 |
| 修改项目配置 | Project | 修改名称、仓库、默认分支、CI 策略 | 修改仓库可能使未交付任务需要重新校验 |
| 初始化工程与 CI | Project、Workflow、Task、TaskAssignment、TaskPackage、AuditLog | 在 `CI_NOT_CONFIGURED` 项目中建立最小工程骨架、构建测试入口和第一条 CI；任务初始分配给创建者 Leader；成功后项目变为 `CI_REQUIRED` | 只能走专用入口；不能跳过 Provider 实际检查，也不能重复用于普通交付或后续 CI 修改 |
| 添加成员 | ProjectMember、AuditLog、Notification | 增加项目访问权限 | 用户必须存在 |
| 移除成员 | ProjectMember、TaskAssignment | 移除访问权 | 未完成任务必须先转派 |
| 查看成员画像 | ProjectMember、MemberProfileVersion | 查看项目内能力、职责和限制 | 不改变画像 |
| 管理自己的画像 | ProjectMember、MemberProfileVersion、AuditLog | Leader 也可以更新自己的开发画像 | 只能修改自己的画像 |
| 归档项目 | Project、Workflow、AuditLog | 禁止新建业务数据，保留历史 | 不物理删除数据 |
| 查看项目看板 | 聚合查询 | 读取 Task、PR、CI 状态 | 不改变业务实体 |

## 3. Workflow 和文档操作

| Leader 操作 | 直接影响实体 | 可能的连带影响 |
|---|---|---|
| 查看 Workflow | Workflow、DocumentVersion、Task、AgentRun | 只读 |
| 修改 Design | DocumentVersion、AuditLog | 后续 Spec/Plan 可能标记 `STALE` |
| 修改 Spec | DocumentVersion、AuditLog | Plan、Task、TaskPackage 可能失效 |
| 请求重新生成 | AgentRun、OutboxJob、AuditLog | 生成新文档候选版本 |
| 修改 Build Plan | DocumentVersion、AuditLog | 未批准计划内容变化 |
| 批准 Build Plan | Workflow、DocumentVersion、AuditLog | 锁定批准版本；Feature/Change 可创建 Task，Architecture 进入架构基线完成路径 |
| 创建 Task | Task、TaskAssignment、TaskPackage | 仅适用于 Feature/Change；Architecture 只能创建子 Intent |
| 取消 Workflow | Workflow、Task、AgentRun、OutboxJob | 未完成任务和后台任务停止；历史 Git/CI 保留 |
| 关闭 Workflow | Workflow、Project、AuditLog | 普通 Feature/Change 必须满足项目 CI 门禁；`CI_BOOTSTRAP` 关闭时同时切换项目 `ci_status` |

原则：

- 文档不覆盖，修改必须创建新版本；
- 批准动作必须绑定具体版本；
- 已批准计划视为不可变快照；
- 影响现有开发上下文时，相关 Task Package 必须变为 `STALE`。

## 4. 计划编辑与分配

### 4.1 Leader 编辑内容

Leader 可以修改：

- Intent 层级允许的计划内容；
- 任务 key、标题和描述；
- Scope；
- Non-goals；
- Acceptance Criteria；
- Verification Commands；
- 建议负责人和分配原因；
- Feature/Change 的分工模式、推荐/实际人数和任务拆分；
- Architecture 的子 Intent、边界和约束；
- 分支名；
- 本地 Agent Git 执行策略。

### 4.2 不允许直接修改的字段

以下字段由系统维护：

- Project ID；
- Workflow ID；
- Task ID；
- Plan 版本号；
- AgentRun ID；
- 创建者和创建时间；
- 已有 Commit、PR、CI 事实；
- 审计日志。

允许高级 JSON 编辑，但主交互应为表格/表单。保存时必须通过 JSON Schema 和资源校验。

## 5. Task 操作

| Leader 操作 | 影响实体 | 规则 |
|---|---|---|
| 分配任务 | Task、TaskAssignment、TaskPackage、Notification | 负责人必须是项目成员且画像已完成；负责人可以是 Leader |
| 重新分配 | Task、TaskAssignment、TaskPackage、Notification | 旧包失效；已开发任务需说明原因 |
| 修改任务 | Task、TaskPackage、AuditLog | 新 Task 版本；影响范围时阻塞并重新确认 |
| 查看阻塞 | TaskBlocker、Task | 只读查看证据和问题 |
| 回复阻塞 | TaskBlocker、DocumentVersion、TaskPackage | 回复或变更可能生成新包 |
| 解决阻塞 | TaskBlocker、Task、Notification | 成员确认新包后恢复 |
| 请求返工 | Task、CIRun、AuditLog | CI 失败后回到开发或重新交付 |
| 取消任务 | Task、AgentRun、OutboxJob | 不自动删除分支或关闭 PR |
| 查看交付 | GitOperation、CIRun | 只读外部事实 |
| 手动同步 | OutboxJob、GitOperation、CIRun | 异步执行，失败可重试 |
| 完成任务 | Task、AuditLog | 必须满足 CI 和 Blocker 条件 |

## 6. Git、PR、CI 操作

Leader 可以：

- 查看分支、Commit、PR 和 CI；
- 代成员登记交付信息；
- 触发同步和重试；
- 请求返工；
- 在满足条件后确认任务完成。

Leader 默认不能：

- 手工把 CI 改成 `PASSED`；
- 将不存在的 Commit 标记为有效；
- 绕过项目仓库校验；
- 自动 Merge PR；
- 删除成员远程分支；
- 修改历史 Git/CI 事实。
- 在 `CI_NOT_CONFIGURED` 项目中将普通 Feature/Change 标记为无需 CI；

MVP 暂不提供强制完成。未来如果支持人工豁免，必须单独的 `MANUAL_OVERRIDE` 动作、原因和审计。

## 7. Blocker 操作

### 被分配成员提交 Blocker

```text
Task = BLOCKED
TaskBlocker = OPEN
Workflow.health = NEEDS_ATTENTION
Notification -> Leader/创建者
AuditLog
```

### Leader 处理 Blocker

可能产生：

```text
TaskBlocker = RESOLVED/CANCELLED
DocumentVersion + 1
Task.version + 1
TaskPackage.old = STALE
TaskPackage.new = CURRENT
Notification -> Member
```

Workflow 默认不改为 `BLOCKED`，其他任务可以继续。

## 8. 实体影响总表

| 实体 | Leader 可做的事 | 是否允许覆盖历史 |
|---|---|---|
| Project | 创建、修改、归档 | 不能删除历史 |
| ProjectMember | 添加、移除、调整项目角色 | 保留变更审计 |
| Workflow | 审批、取消、关闭、重新规划 | 状态变更留痕 |
| DocumentVersion | 新建修改版、确认、废弃 | 不允许覆盖 |
| Task | 创建、分配、修改、返工、取消、完成 | 保留版本和历史 |
| TaskPackage | 生成、标记过期、重新生成 | 不允许覆盖 |
| TaskBlocker | 查看、回复、解决、取消 | 保留处理历史 |
| AgentRun | 查看、重试、取消 | 不允许删除 |
| GitOperation | 登记、校验、查询 | 不修改外部事实 |
| CIRun | 查询、同步、重试 | 不伪造结果 |
| Notification | 查看、标记已读 | 可更新已读状态 |
| AuditLog | 查询 | 不允许修改和删除 |

## 9. 审计字段

Leader 的所有写操作至少记录：

```text
actorUserId
projectId
action
entityType
entityId
beforeVersion
afterVersion
reason
requestId
createdAt
```
