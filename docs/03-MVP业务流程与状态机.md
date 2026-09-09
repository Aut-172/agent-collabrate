# MVP 业务流程与状态机

## 1. 状态实体的职责

平台不使用一个状态字段表达所有事情：

| 实体 | 负责表达 |
|---|---|
| Workflow | 一个完整需求从意图到关闭的生命周期 |
| Task | 一个可分配开发单元的生命周期 |
| AgentRun | 一次平台侧 Agent 调用 |
| TaskPackage | 某个任务交接上下文的版本 |
| TaskDelivery | 一次本地开发交付尝试及其 Final Report |
| TaskBlocker | 一个待解决的问题 |
| GitOperation | 一次分支、Commit 或 PR 事实记录 |
| CIRun | 某个 Commit 的 CI 运行 |
| Workflow.health | 工作流是否需要关注，不替代 Workflow.status |

## 2. Workflow 状态

```text
INTENT
  -> DESIGN_PROPOSED
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

终止状态：

```text
CANCELLED
FAILED
```

说明：

- `SPEC_PROPOSED` 表示 Agent 或用户已生成规格，但尚未确认；
- `SPEC_CONFIRMED` 表示创建者已确认具体 Spec 版本；
- `PLAN_APPROVED` 表示 Leader 批准具体 Build Plan 版本；
- `TASKS_READY` 表示任务已按批准计划创建；
- `DELIVERY_SUBMITTED` 表示至少有任务提交交付信息；
- `CI_RUNNING` 表示当前交付 Commit 的必要 CI 正在执行；
- `CI_PASSED` 表示当前交付 Commit 的必要 CI 已通过；
- `READY_TO_CLOSE` 表示所有必要 Task 完成，等待 Leader 关闭；
- `DONE` 只能由满足全部完成条件的关闭动作产生。

## 3. Task 状态

```text
TODO
ASSIGNED
IN_PROGRESS
BLOCKED
DELIVERY_SUBMITTED
CI_RUNNING
DONE
FAILED
CANCELLED
```

状态含义：

| 状态 | 含义 |
|---|---|
| `TODO` | 已创建但尚未分配 |
| `ASSIGNED` | 已分配负责人，尚未开始 |
| `IN_PROGRESS` | 负责人已确认当前任务包并开始开发 |
| `BLOCKED` | 有需要人工处理的问题 |
| `DELIVERY_SUBMITTED` | 已登记分支、Commit 或 PR |
| `CI_RUNNING` | 当前 Commit 的 CI 正在执行 |
| `DONE` | 当前 Commit 的必要 CI 通过且任务完成 |
| `FAILED` | 平台无法继续处理或任务被判定失败 |
| `CANCELLED` | 任务被取消 |

## 4. AgentRun 状态

```text
QUEUED -> RUNNING -> SUCCEEDED
                    -> FAILED
                    -> CANCELLED
```

AgentRun 的成功不等于 Workflow 成功。只有输出被校验并保存为正确的文档版本后，才允许推进 Workflow。

## 5. TaskPackage 状态

```text
CURRENT
STALE
RETIRED
```

- `CURRENT`：任务当前可使用的版本；
- `STALE`：已有新版本，旧版本不能用于开始开发或提交交付；
- `RETIRED`：任务取消、工作流关闭或版本被明确废弃。

## 6. TaskBlocker 状态

```text
OPEN
RESOLVED
CANCELLED
```

## 7. CI 状态

```text
PENDING
RUNNING
PASSED
FAILED
CANCELLED
UNKNOWN
```

## 8. TaskDelivery 状态

```text
SUBMITTED
CI_RUNNING
PASSED
FAILED
REJECTED
```

- `SUBMITTED`：平台已保存并初步接受交付报告；
- `CI_RUNNING`：当前 Commit 的必要 CI 正在执行；
- `PASSED`：当前 Commit 的必要 CI 已通过；
- `FAILED`：当前 Commit 的必要 CI 失败；
- `REJECTED`：任务包过期、报告格式错误、Commit/PR 校验失败等导致平台拒绝交付。

## 9. Workflow 状态转换表

| 当前状态 | 事件 | 条件 | 目标状态 |
|---|---|---|---|
| `INTENT` | Design Agent 成功 | 生成有效 Design 版本 | `DESIGN_PROPOSED` |
| `DESIGN_PROPOSED` | 请求生成 Spec | Design 存在 | `SPEC_PROPOSED` 或等待 AgentRun |
| `SPEC_PROPOSED` | Spec 成功 | 生成有效 Spec 版本 | `SPEC_PROPOSED` |
| `SPEC_PROPOSED` | 创建者确认 | 确认指定版本 | `SPEC_CONFIRMED` |
| `SPEC_CONFIRMED` | 请求生成 Plan | Spec 已确认 | `BUILD_PLAN_PROPOSED` 或等待 AgentRun |
| `BUILD_PLAN_PROPOSED` | Leader 修改 | Schema 通过 | `BUILD_PLAN_PROPOSED` |
| `BUILD_PLAN_PROPOSED` | Leader 批准 | 指定版本有效 | `PLAN_APPROVED` |
| `PLAN_APPROVED` | 创建 Task | 计划已批准 | `TASKS_READY` |
| `TASKS_READY` | 首个 Task 开始 | 至少一个 Task 开始 | `IN_PROGRESS` |
| `IN_PROGRESS` | 提交交付 | 有效 Git 记录 | `DELIVERY_SUBMITTED` |
| `DELIVERY_SUBMITTED` | CI 开始 | 当前 Commit 有 CI | `CI_RUNNING` |
| `CI_RUNNING` | CI 通过 | 所有必要检查通过 | `CI_PASSED` |
| `CI_PASSED` | 所有 Task 完成 | 所有必要 Task 为 `DONE` | `READY_TO_CLOSE` |
| `READY_TO_CLOSE` | Leader 关闭 | 无未解决 Blocker | `DONE` |
| 任意未完成状态 | 取消 | 操作者有权限 | `CANCELLED` |

Agent、Git 或 CI 失败不自动把 Workflow 改为 `FAILED`。失败结果必须记录，是否重试或调整由业务动作决定。

## 10. Task 状态转换表

| 当前状态 | 事件 | 操作者 | 目标状态 |
|---|---|---|---|
| `TODO` | 分配 | Leader | `ASSIGNED` |
| `ASSIGNED` | 确认任务包并开始 | 被分配成员（包括 Leader） | `IN_PROGRESS` |
| `IN_PROGRESS` | 报告阻塞 | 被分配成员 | `BLOCKED` |
| `BLOCKED` | 确认新包并恢复 | 被分配成员 | `IN_PROGRESS` |
| `IN_PROGRESS` | 提交交付 | 被分配成员 | `DELIVERY_SUBMITTED` |
| `DELIVERY_SUBMITTED` | CI 开始 | System | `CI_RUNNING` |
| `CI_RUNNING` | CI 通过 | System | `DONE` |
| `CI_RUNNING` | CI 失败 | System | `IN_PROGRESS` 或保持 `CI_RUNNING`，由项目规则决定 |
| 未完成状态 | 重新分配 | Leader | `ASSIGNED` 或 `IN_PROGRESS` |
| 未完成状态 | 取消 | Leader | `CANCELLED` |

## 11. 核心不变量

1. 状态转换必须校验当前状态，禁止 Controller 直接写状态；
2. 状态转换必须校验操作者和项目成员关系；
3. 文档版本、任务包版本和审计记录不可覆盖；
4. 已批准 Plan 的内容不能被静默修改；
5. 任务交付必须绑定一个明确的 Commit SHA；
6. CI 通过记录的 `head_sha` 必须等于当前交付 Commit；
7. 新 Commit 产生后，旧 CI 结果不能继续证明任务完成；
8. 有未解决 Blocker 时不能完成 Task；
9. 单个 Task `BLOCKED` 不自动把 Workflow 设为 `BLOCKED`；
10. 只有 `READY_TO_CLOSE` 才能关闭 Workflow；
11. 不允许通过请求参数直接把 CI 写成 `PASSED`；
12. 不允许通过 URL ID 跨项目访问资源。

## 12. 任务包过期规则

以下事件会使当前 Task Package 变为 `STALE`：

- Spec 版本变化；
- Build Plan 版本变化；
- Task 范围、验收标准或测试命令变化；
- Task 负责人变化；
- base branch 或 base commit 变化；
- 任务被重新打开或返工；
- Leader 解决 Blocker 时要求变更任务上下文。

通知只是提醒，正确性由两个强制检查保证：

```text
开始开发时校验 packageVersion
提交交付时再次校验 packageVersion
```

版本不一致时返回 `409 TASK_PACKAGE_STALE`，并提供当前包和差异地址。

## 13. Blocker 规则

### 12.1 触发方式

本地 Agent 可以按照任务包输出结构化阻塞报告，但平台无法自动知道本地执行结果。成员确认后，通过平台提交 Blocker，Task 才进入 `BLOCKED`。

### 12.2 Blocker 原因

```text
REQUIREMENT_CLARIFICATION
SPEC_CONFLICT
DEPENDENCY
ENVIRONMENT
PERMISSION
CI_FAILURE
TASK_PACKAGE_UPDATED
OTHER
```

### 12.3 Blocker 解决

Leader/创建者可以回复、修改文档、修改任务、换人或取消任务。任何影响开发上下文的变更都必须产生新版本和新任务包。

## 14. 看板映射

看板不新增状态，只按 Task 状态聚合：

| 看板列 | Task 状态 |
|---|---|
| 待处理 | `TODO` |
| 已分配 | `ASSIGNED` |
| 开发中 | `IN_PROGRESS` |
| 阻塞 | `BLOCKED` |
| 待交付 | `DELIVERY_SUBMITTED` |
| CI 中 | `CI_RUNNING` |
| 已完成 | `DONE` |
| 已取消/失败 | `CANCELLED`、`FAILED` |

MVP 不支持拖拽看板直接改状态，状态变更必须通过具名业务动作。
