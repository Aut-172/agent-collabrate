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

## 1.1 Intent 层级与处理管线

Workflow 的 `intent_level` 与生命周期状态是两个维度，不能把层级编码进状态名。

```text
ARCHITECTURE
  Intent -> Architecture Design -> Architecture Spec
  -> Child Intent Plan -> Leader Approval -> 创建子 Intent

FEATURE
  Intent -> Feature Design -> Feature Spec -> Build Plan
  -> AI Staffing Recommendation -> Leader Approval
  -> Tasks -> Delivery / CI

CHANGE
  Intent -> Change Plan -> AI Staffing Recommendation
  -> Leader Approval -> Task -> Delivery / CI
```

规则：

- `ARCHITECTURE` 不产生开发分工、TaskAssignment 或代码交付型 Task；
- `FEATURE` 和 `CHANGE` 必须生成 AI 分工建议；
- Feature 默认倾向于比 Change 推荐更多成员，但这是 AI 的软规则，不是固定人数约束；
- AI 必须基于范围、依赖、任务工作量、成员画像和当前工作量解释推荐人数；
- Leader 批准后才创建实际 TaskAssignment；
- Architecture 完成表示架构基线已批准，子 Intent 已创建或明确暂不拆分，不等待子 Intent 的代码完成。

## 2. Workflow 状态

Workflow 还需要一个独立的 `completion_mode`：

```text
ARCHITECTURE_BASELINE
CI_BOOTSTRAP
CI_REQUIRED
```

- `ARCHITECTURE_BASELINE`：架构级 Workflow，无代码交付，不要求 CI；
- `CI_BOOTSTRAP`：项目尚未配置 CI 时，专门建立最小工程骨架、构建测试入口并验证第一条 CI 管线；
- `CI_REQUIRED`：普通 Feature/Change Workflow，必须满足当前 Commit 的 CI 门禁。

`CI_BOOTSTRAP` 只能通过专用接口由项目 Leader 创建，服务端固定其 `intent_level = FEATURE`，开发任务固定初始分配给该 Workflow 的创建者 Leader。它不用于后续 CI 配置维护；CI 已初始化后的配置修改属于普通 Change Workflow。

`completion_mode` 不替代 `intent_level`。例如，一个从零搭建项目骨架并同时建立 CI 的 Feature，可以是：

```text
intent_level = FEATURE
completion_mode = CI_BOOTSTRAP
```

默认映射：

| 条件 | 允许的 `completion_mode` |
|---|---|
| `intent_level = ARCHITECTURE` | 只能是 `ARCHITECTURE_BASELINE` |
| 项目 `ci_status = CI_NOT_CONFIGURED`，且项目尚无成功 Bootstrap | 只有专门初始化工程与 CI 的一个 Feature Workflow 可以是 `CI_BOOTSTRAP` |
| 项目 `ci_status = CI_REQUIRED` 的普通 Feature/Change | `CI_REQUIRED` |

在 `CI_NOT_CONFIGURED` 阶段，普通 Feature/Change 可以被记录为待规划事项，但在专用的工程与 CI 初始化 Workflow 完成前，不能创建可关闭的普通开发交付。

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

终止状态：

```text
CANCELLED
FAILED
```

说明：

- `SPEC_PROPOSED` 表示 Agent 或用户已生成规格，但尚未确认；
- `DESIGN_CONFIRMED` 表示创建者已确认具体 Design 版本，允许生成 Spec；
- `SPEC_CONFIRMED` 表示创建者已确认具体 Spec 版本；
- `PLAN_APPROVED` 表示 Leader 批准具体 Build Plan 版本；
- `TASKS_READY` 表示任务已按批准计划创建；
- `DELIVERY_SUBMITTED` 表示至少有任务提交交付信息；
- `CI_RUNNING` 表示当前交付 Commit 的必要 CI 正在执行；
- `CI_PASSED` 表示当前交付 Commit 的必要 CI 已通过；
- `READY_TO_CLOSE` 表示所有必要 Task 完成，等待 Leader 关闭；
- `DONE` 只能由满足全部完成条件的关闭动作产生。
- 对 `ARCHITECTURE` Workflow，`PLAN_APPROVED` 表示架构基线已批准，随后可直接进入 `READY_TO_CLOSE`，前提是子 Intent 已创建或明确暂不拆分；
- 对 `ARCHITECTURE` Workflow，`TASKS_READY`、`IN_PROGRESS`、`DELIVERY_SUBMITTED` 和 `CI_*` 不代表架构本身必须经过代码交付，这些阶段只适用于产生开发 Task 的 Feature/Change。
- 对 `CI_BOOTSTRAP` Workflow，`CI_PASSED` 指引导 CI 在当前 Bootstrap Commit 上成功，不要求项目在 Bootstrap 开始前已有 CI；
- Bootstrap 成功关闭后，Project 的 `ci_status` 必须变为 `CI_REQUIRED`。

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
| `DESIGN_PROPOSED` | 创建者确认 Design | 确认指定版本 | `DESIGN_CONFIRMED` |
| `DESIGN_CONFIRMED` | 请求生成 Spec | Design 已确认 | `SPEC_PROPOSED` 或等待 AgentRun |
| `SPEC_PROPOSED` | Spec 成功 | 生成有效 Spec 版本 | `SPEC_PROPOSED` |
| `SPEC_PROPOSED` | 创建者确认 | 确认指定版本 | `SPEC_CONFIRMED` |
| `SPEC_CONFIRMED` | 请求生成 Plan | Spec 已确认 | `BUILD_PLAN_PROPOSED` 或等待 AgentRun |
| `BUILD_PLAN_PROPOSED` | Leader 修改 | Schema 通过 | `BUILD_PLAN_PROPOSED` |
| `BUILD_PLAN_PROPOSED` | Leader 批准 | 指定版本有效 | `PLAN_APPROVED` |
| `PLAN_APPROVED` | 创建 Task | 计划已批准 | `TASKS_READY` |
| `PLAN_APPROVED`（`ARCHITECTURE`） | 确认架构基线 | 子 Intent 已创建或明确暂不拆分 | `READY_TO_CLOSE` |
| `TASKS_READY` | 首个 Task 开始 | 至少一个 Task 开始 | `IN_PROGRESS` |
| `IN_PROGRESS` | 提交交付 | 有效 Git 记录 | `DELIVERY_SUBMITTED` |
| `DELIVERY_SUBMITTED` | CI 开始 | 当前 Commit 有 CI | `CI_RUNNING` |
| `CI_RUNNING` | CI 通过 | 所有必要检查通过 | `CI_PASSED` |
| `CI_PASSED` | 所有 Task 完成 | 所有必要 Task 为 `DONE` | `READY_TO_CLOSE` |
| `CI_PASSED`（`CI_BOOTSTRAP`） | Leader 确认引导结果 | CI 配置已识别且 Bootstrap 检查通过 | `READY_TO_CLOSE`，并将 Project 设为 `CI_REQUIRED` |
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
12. 不允许通过 URL ID 跨项目访问资源；
13. Architecture 不得创建开发分工或开发 Task；
14. Feature/Change 的分工建议必须有推荐人数、模式、理由和工作量依据；
15. 已批准并开始执行的 Task 不因成员工作量变化自动换人；
16. `CI_NOT_CONFIGURED` 项目中，普通 Feature/Change Workflow 不得绕过 `CI_BOOTSTRAP` 关闭；
17. `CI_BOOTSTRAP` 只能用于建立和验证第一条 CI 管线，不能被重复用于普通功能交付。

18. Feature/Change/CI_BOOTSTRAP Workflow 的 pull_request_required 默认必须为 TRUE；关闭后可以无 PR 交付，但不能绕过任务分支、Commit 和 CI 校验；Architecture 固定为不适用。
19. 每次 Workflow 创建后必须先完成一次 Repo Inventory/Code Context 刷新；刷新期间旧索引和旧上下文标记为 `STALE`，不得生成新的 Context Plan。项目任一成员都可以发起同步。

## 12. 任务包过期规则

以下事件会使当前 Task Package 变为 `STALE`：

- Spec 版本变化；
- Build Plan 版本变化；
- Code Context 版本变化并影响当前任务范围或代码基线；
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

### 13.1 触发方式

本地 Agent 可以按照任务包输出结构化阻塞报告，但平台无法自动知道本地执行结果。成员确认后，通过平台提交 Blocker，Task 才进入 `BLOCKED`。

### 13.2 Blocker 原因

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

### 13.3 Blocker 解决

Leader/创建者可以回复、修改文档、修改任务、换人或取消任务。任何影响开发上下文的变更都必须产生新版本和新任务包。

MVP 中关闭 `OPEN` Blocker 本身即触发任务包重新生成：旧包变为 `STALE`，新包记录已关闭 Blocker 的原因、摘要、状态和解决说明。Task 保持 `BLOCKED`，直到当前负责人确认新包；该确认记录类型为 `RESUME_AFTER_BLOCKER`，确认成功后 Task 才回到 `IN_PROGRESS`。

同一 Task 同时最多存在一个 `OPEN` Blocker。任一 Task 存在 `OPEN` Blocker 时 Workflow 主状态保持不变，健康度为 `NEEDS_ATTENTION`，且 Workflow 不能关闭；最后一个 `OPEN` Blocker 关闭后健康度恢复为 `HEALTHY`。

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
