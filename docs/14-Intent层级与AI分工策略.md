# Intent 层级与 AI 分工策略

## 1. 目的

当前平台的 Intent 不是同一种粒度的需求。Architecture、Feature 和 Change 的产物、完成条件和人员分配方式不同，不能全部套用同一条：

```text
Intent -> Design -> Spec -> Build Plan -> Task -> Delivery
```

本文件定义 Intent 层级与人员分工之间的关系。

## 2. Intent 层级

| 层级 | 主要目标 | 是否产生开发分工 |
|---|---|---|
| `ARCHITECTURE` | 建立系统/子系统架构、边界和约束 | 否 |
| `FEATURE` | 交付一个可验证的功能或能力 | 是 |
| `CHANGE` | 完成局部代码、配置或小范围行为变更 | 是 |

父子关系示例：

```text
ARCHITECTURE：建设统一认证体系
  -> FEATURE：实现用户登录
  -> FEATURE：实现权限校验
  -> CHANGE：增加登录失败次数限制
```

## 3. Architecture Intent

Architecture Intent 不产生开发 Task，也不产生开发成员分配。

它的流程是：

```text
Intent
  -> Architecture Design
  -> Architecture Spec
  -> Child Intent Plan
  -> Leader Approval
  -> 创建子 Intent
```

主要输出：

- 系统边界；
- 模块和服务边界；
- 技术约束；
- 数据和接口原则；
- 非功能需求；
- 风险和依赖；
- 子 Intent 建议。

Architecture Agent 的输出中不得包含：

- `suggestedAssignee`；
- `teamSize`；
- `taskAssignments`；
- 面向代码提交的 Task Package。

Architecture 可以产生“建议由谁参与架构评审”的信息，但这不是开发分工，不进入 TaskAssignment。

Architecture 的 `childIntents` 只允许 `FEATURE` 或 `CHANGE`。平台会在 Build Plan 保存/批准以及创建子 Intent 时拒绝 `ARCHITECTURE -> ARCHITECTURE` 的递归嵌套。需要更深一层架构基线时，应创建新的根 Architecture Workflow；这样既保留按语义拆分工程纵深的能力，也避免 Intent 流程无限递归。

每个子 Intent 必须包含可直接作为新 Workflow 初始意图的 `title`、`description` 和 `intentLevel`。Leader 批准 Architecture Build Plan 后，平台会以当前 Architecture Workflow 为父级，自动创建对应的 Feature/Change Workflow，并从 `INTENT` 状态开始其独立的 Design/Spec/Build Plan 流程；`parentWorkflowId`、完成模式和 PR 策略由平台根据父级与 Intent 层级补齐，不由 Agent 自行填写。

Architecture Intent 的完成条件是：

```text
架构基线已被 Leader 批准
并且子 Intent 已创建或明确记录为暂不拆分
```

它不需要等待所有子 Intent 的代码交付完成。

## 4. Feature Intent

Feature Intent 默认进入团队分工流程：

```text
Intent
  -> Feature Design
  -> Feature Spec
  -> Build Plan
  -> Staffing Recommendation
  -> Leader Approval
  -> Tasks
  -> Local Development
  -> Delivery / CI
```

Feature 通常包含多个相互关联的 Task，默认允许多个成员并行或协作完成。

但“Feature 一定多人”不是硬规则。简单 Feature 可以由一个人完成，最终由 AI 根据任务规模、依赖、验收标准和成员工作量裁定建议人数。

## 5. Change Intent

Change Intent 默认使用精简流程：

```text
Intent
  -> Implementation Plan
  -> Staffing Recommendation
  -> Leader Approval
  -> Task
  -> Local Development
  -> Delivery / CI
```

Change 通常由一个成员负责，复杂或高风险 Change 可以建议两人协作，但不应默认拆成多人团队。

## 6. 分工策略

### 6.1 分工模式

```text
NONE
SINGLE_OWNER
PAIR
TEAM
```

| 模式 | 使用场景 |
|---|---|
| `NONE` | Architecture |
| `SINGLE_OWNER` | 普通 Change 或小型 Feature |
| `PAIR` | 高风险局部修改、需要复核的 Change |
| `TEAM` | 中大型 Feature 或跨模块 Feature |

AI 需要输出推荐模式和理由，但最终由 Leader 批准。

这里的“推荐人数”指参与该 Intent 的不同项目成员数量，不是 Task 数量。一个成员可以负责多个相互关联的 Task，但仍只计为 1 人。

默认人数基线：

| Intent 层级 | 默认分工基线 | 允许的 AI 调整 |
|---|---|---|
| `ARCHITECTURE` | 0 名开发负责人 | 可以建议架构评审参与者，但不形成开发分工 |
| `CHANGE` | 1 名成员 | 高风险、跨模块或需要双人复核时可调整为 2 名或更多 |
| `FEATURE` | 通常 2 名或更多 | 简单、边界清晰且无并行价值时可调整为 1 名；跨模块时可增加成员 |

Feature 比 Change 通常推荐更多成员是先验倾向，不是数据库约束，也不是必须满足的审批条件。AI 给出例外结果时，必须说明：

- 为什么实际范围不足以支持默认人数；
- 哪些任务可以并行，哪些任务必须串行；
- 增加成员是否会带来协调成本；
- 当前成员工作量和能力画像如何影响最终选择。

### 6.2 AI 分工输入

Agent 生成分工建议时至少接收：

- `intent_level`；
- Intent 目标、范围和非目标；
- 当前父 Intent 的架构约束；
- 当前 Repo Inventory、Context Plan、Code Context 版本、base Commit、相关文件证据和约束；
- Feature/Change 的 Design、Spec 和 Build Plan；
- Task 估算工作量；
- 项目内所有可分配成员的能力画像；
- 每个成员当前未完成任务和工作量；
- 成员可投入程度；
- 成员的任务偏好和限制；
- 任务之间的依赖和并行关系。

Architecture Intent 不读取成员画像来产生开发分工。它只读取架构、项目和代码上下文，用于形成系统边界、约束和子 Intent 建议。

### 6.3 成员候选资格

成员只有同时满足以下条件，才可以被 Feature 或 Change 分配：

```text
project_members.status = ACTIVE
project_members.profile_completed = TRUE
用户处于可分配状态
不违反项目或任务限制
```

Leader 和 Member 的开发分配资格相同。Leader 的额外权限只影响审批、成员管理和项目治理。

## 7. 工作量建模

AI 不能只根据“当前有几个任务”判断工作量。建议每个 Feature/Change Task 具有：

```text
effort_points: 1 - 8
priority
optional due_at
```

成员画像包含：

```text
weekly_capacity_points
availability
```

当前工作量至少由以下信息计算：

```text
open_effort_points
= 所有未完成 Task 的 effort_points 总和
```

包含：

- `ASSIGNED`；
- `IN_PROGRESS`；
- `BLOCKED`；
- `DELIVERY_SUBMITTED`；
- `CI_RUNNING`。

已完成和已取消任务不计入当前工作量。阻塞任务不能默认视为释放了工作量，因为成员可能仍需继续处理。

MVP 不要求精确预测工时，优先使用相对工作量点数，避免制造虚假的时间精度。

## 8. AI 输出格式

### Architecture

```json
{
  "intentLevel": "ARCHITECTURE",
  "architectureGoals": [],
  "systemBoundaries": [],
  "constraints": [],
  "nonFunctionalRequirements": [],
  "childIntents": [
    {
      "title": "统一认证登录",
      "description": "实现用户登录、会话建立和登录成功验收，作为独立功能 Workflow 继续拆解",
      "intentLevel": "FEATURE"
    }
  ],
  "risks": []
}
```

不得包含开发分工字段。

### Feature / Change

```json
{
  "intentLevel": "FEATURE",
  "staffingRecommendation": {
    "mode": "TEAM",
    "recommendedTeamSize": 3,
    "reason": "涉及认证、权限和审计三个模块，可并行开发",
    "confidence": 0.82
  },
  "assignments": [
    {
      "taskKey": "TASK-001",
      "userId": 12,
      "projectRole": "LEADER",
      "profileVersion": 2,
      "workloadSnapshot": {
        "openEffortPoints": 5,
        "weeklyCapacityPoints": 13
      },
      "fitReason": "熟悉 Spring Security 和认证系统",
      "assignmentScore": 0.91
    }
  ],
  "alternatives": [],
  "warnings": []
}
```

`assignments` 仍然只是建议。Leader 可以修改负责人、人数、拆分方式和理由。

所有层级的 Agent 输出都应引用使用的 Code Context。Architecture 引用代码证据来说明现有架构；Feature/Change 引用代码证据来说明影响范围、任务拆分和验收依据。Git Provider 不参与分工判断，也不决定相关文件；平台 Agent 先生成 Context Plan，再基于 Orchestrator 取回的 Code Evidence 生成分工建议。

## 9. 分配评价维度

建议 Agent 按以下维度综合判断：

### 硬约束

- 是否为项目 ACTIVE 成员；
- 是否填写能力画像；
- 是否具备任务所需的基本技能；
- 是否违反成员限制；
- 是否存在任务依赖冲突；
- 是否超过明确的并行任务上限。

### 软约束

- 技能匹配度；
- 当前工作量；
- 可投入程度；
- 任务偏好；
- 领域连续性；
- 团队负载均衡；
- Leader 是否需要保留审核时间。

AI 必须在输出中给出理由和警告，不能只返回一个 user ID。

## 10. 分配审批和快照

分工流程：

```text
Agent 生成 Staffing Recommendation
  -> Leader 修改或接受
  -> Leader 批准具体版本
  -> 创建 TaskAssignment
  -> 保存画像快照和工作量快照
  -> 生成 Task Package
```

`TaskAssignment` 至少保存：

```text
assignment_reason
assignment_score
profile_version
profile_snapshot
workload_snapshot
```

成员后续修改画像或完成其他任务，不改变历史分配依据。

## 11. 重新评估时机

以下情况可以触发尚未批准分工建议的重新生成：

- Feature/Change 的范围变化；
- Task effort_points 变化；
- 成员能力画像变化；
- 成员被分配了新的任务；
- 成员完成或取消任务；
- 成员可投入程度变化；
- 任务依赖发生变化。

已经批准并开始执行的 Task 不应因为工作量变化而自动换人。需要由 Leader 发起重新分配，并生成新的任务包版本。

## 12. 前端要求

Feature/Change 的计划审批页需要显示：

```text
推荐分工模式
推荐人数
每个成员的任务
成员能力匹配理由
当前工作量 / 可投入容量
分配风险和警告
备选分配方案
```

Architecture 的审批页不显示成员分工表，而显示：

```text
架构目标
系统边界
约束
非功能需求
子 Intent 建议
风险
```

## 13. MVP 边界

MVP 可以先实现：

1. 三种 Intent 层级；
2. Architecture 不产生开发分工；
3. Feature/Change 产生 AI 分工建议；
4. Leader 审批和修改分工；
5. 成员画像参与分配；
6. Task 增加 `effort_points`；
7. 分配记录保存画像和工作量快照。

MVP 暂不要求：

- 自动调度正在执行的任务；
- 自动换人；
- 精确工时预测；
- 基于历史绩效的复杂评分；
- 多目标优化算法；
- 独立的资源管理系统。
