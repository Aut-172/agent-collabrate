# AI Agent 协作开发平台

## MVP 需求分析与 PRD

## 1. 产品定位

这是一个面向小型开发团队的 Agent 协作编排平台。它把自然语言意图和可追溯代码上下文转化为经过确认的设计、规格、构建计划和可分配任务，并记录成员使用本地 Agent 开发后的 Git、PR 和 CI 交付结果。

平台的价值不是代替开发者操作本地代码，而是让团队形成一条可追踪的链路：

```text
意图 -> 设计 -> 规格 -> 构建计划 -> 任务 -> 本地开发 -> PR/CI -> 完成
```

## 2. 用户角色

### 2.1 Leader

项目治理者，同时也是可以被 Agent 分配开发任务的项目成员。Leader 负责：

- 创建和配置项目；
- 管理项目成员；
- 在加入项目时填写自己的职责和能力画像；
- 审阅和批准构建计划；
- 修改任务和分配建议；
- 分配、重新分配和取消任务；
- 处理阻塞和需求变更；
- 查看项目看板、交付和审计信息；
- 在被分配任务时，按照普通开发成员流程处理本地开发、测试和交付；
- 在完成条件满足后关闭工作流。

### 2.2 Member

项目参与者，负责：

- 在加入项目时填写自己的项目职责和能力画像；
- 创建意图；
- 查看自己参与的工作流；
- 查看分配给自己的任务包；
- 在本地使用 Agent 开发和运行测试；
- 按任务包要求执行 Git 操作；
- 登记 Commit、PR 和测试结果；
- 报告阻塞和需求疑问。

### 2.3 项目成员能力画像

能力画像属于用户在某个项目中的成员关系，不属于全局用户身份。Leader 和 Member 都必须填写，Agent 使用它生成任务分配建议。

画像至少包括：

- 自我介绍和项目职责；
- 技术能力和熟悉领域；
- 经验或擅长的任务类型；
- 希望承担的任务类型；
- 不负责或不熟悉的范围；
- 当前可投入程度；
- 其他分配约束。

画像允许由成员自己更新。更新不会自动改变已经批准的任务分配，但会影响后续未批准的 Build Plan 和分配建议。

### 2.4 System

系统负责：

- 调用平台侧 Agent API；
- 通过 Code Context Provider 获取仓库代码事实；
- 生成和保存文档版本；
- 根据批准计划生成任务；
- 校验权限、状态、版本和 Git/CI 事实；
- 处理异步任务和重试；
- 接收 Webhook 和同步外部状态；
- 写入审计日志。

### 2.5 Intent 层级与分工边界

Intent 必须声明处理层级：

| 层级 | 目标 | 是否生成开发分工 |
|---|---|---|
| `ARCHITECTURE` | 建立系统/子系统架构、边界和约束 | 否 |
| `FEATURE` | 交付一个可验证的功能或能力 | 是 |
| `CHANGE` | 完成局部代码、配置或小范围行为变更 | 是 |

分工人数是 AI 的建议结果，不是用户输入的固定规则。MVP 的默认倾向是：

- Feature 通常比 Change 涉及更大的范围、更多模块或更多并行工作，因此推荐分工人数通常多于 Change；
- Change 通常由单人负责，复杂或高风险 Change 可以由两人协作；
- 简单 Feature 可以由一人完成，AI 必须解释为什么没有采用多人分工；
- AI 必须结合 Intent 范围、任务依赖、成员能力画像、当前工作量和可投入容量裁定人数；
- Leader 可以修改人数、任务拆分和负责人，并对最终结果负责。

Architecture 可以提出架构评审参与者建议，但不得生成面向代码交付的开发分工、负责人或 TaskAssignment。

### 2.6 代码上下文边界

平台 Agent 在生成 Design、Spec 和 Build Plan 前，必须获得与当前仓库相关的代码上下文证据。该证据由 Code Context Provider 提供，而不是由用户手工描述替代。

MVP 当前扩展默认使用 Git Provider 读取远程仓库的默认分支、Commit、目录树、文件元数据和白名单文件，形成 Repo Inventory。随后由平台 Agent 基于 Intent 和 Repo Inventory 生成 Context Plan，再由 Code Context Orchestrator 按计划读取相关文件和 Diff，形成版本化 Code Context。未来可以增加 Local Agent Provider，由本地 Connector 调用 Codex CLI 读取本地仓库并返回结构化证据。

职责划分：

- Git Provider 负责仓库原始事实，不自主判断文件相关性；
- Code Context Orchestrator 负责 Repo Inventory、Context Plan、多轮取证、预算和证据引用；
- 平台 Agent 负责正式 Design、Spec、Build Plan 和分工；
- 本地 Agent 负责按任务包执行代码修改、测试和 Git 操作；
- 本地 Agent 的输出不能绕过平台文档版本、Schema 校验和 Leader 审批。

## 3. 产品目标

### 3.1 MVP 目标

1. 让团队可以把一个自然语言意图变成结构化开发任务；
2. 让 Leader 可以对 Agent 的计划和分配结果进行审核和修改；
3. 让成员获得具有明确上下文、边界和验收标准的任务包；
4. 让成员在本地使用自己熟悉的 Agent 完成代码开发；
5. 让平台能验证并记录 Commit、PR 和 CI 状态，并支持从零项目先建立 CI 门禁；
6. 让关键决策和交付结果可追溯、可审计。

从零项目的推荐顺序是：

```text
创建项目（CI_NOT_CONFIGURED）
  -> 可先完成 Architecture 基线
  -> CI Bootstrap：建立最小 CI 并验证一次
  -> 项目进入 CI_REQUIRED
  -> 正常 Feature/Change 开发
```

### 3.2 非目标

MVP 不实现：

- 平台远程读写成员本地代码；
- 平台托管代码 Agent Runtime；
- 服务器执行 Shell、测试或构建命令；
- 自动合并 Pull Request；
- 多租户和复杂 RBAC；
- 任务依赖图和自动解锁；
- 多种 Git 平台同时支持；
- 高可用、自动伸缩、消息队列；
- 完整监控告警平台；
- 复杂项目管理功能。

## 4. 端到端用户流程

```text
Leader 创建项目并添加成员
  -> Member 创建 Intent
  -> 平台同步或刷新 Code Context
  -> 平台异步生成 Design
  -> 创建者确认 Design
  -> 平台异步生成 Spec
  -> 创建者确认 Spec
  -> 平台异步生成 Build Plan
  -> 对 Feature/Change 生成分工建议；Architecture 只生成子 Intent 建议
  -> Leader 修改或批准计划
  -> 平台根据批准版本创建 Task（Architecture 不创建开发 Task）
  -> 平台生成 Task Package
  -> Leader 分配或确认负责人
  -> Member 下载任务包并在本地执行
  -> Member 本地 Agent 修改代码、测试、Commit、Push
  -> Member 创建或登记 PR
  -> 平台同步 Commit、PR 和 CI
  -> 当前 Commit 的必要检查通过
  -> Task 完成
  -> 全部必要 Task 完成
  -> Leader 关闭 Workflow
```

## 5. 功能需求

### 5.1 用户与项目

| 编号 | 需求 | 优先级 |
|---|---|---|
| FR-001 | 用户可以登录平台 | P0 |
| FR-002 | 首个 Leader 可以通过初始化配置创建 | P0 |
| FR-003 | Leader 可以创建项目 | P0 |
| FR-004 | Leader 可以添加和移除项目成员 | P0 |
| FR-005 | 项目包含 Git 仓库地址、默认分支和 Git Provider | P0 |
| FR-005A | 新项目初始标记为 `CI_NOT_CONFIGURED`，Leader 可以发起一次 CI Bootstrap Workflow | P0 |
| FR-005B | CI Bootstrap 成功后项目切换为 `CI_REQUIRED` | P0 |
| FR-006 | 归档项目后不允许新建工作流和任务 | P1 |
| FR-007 | 成员加入项目时必须填写项目能力画像 | P0 |
| FR-008 | Leader 也必须填写能力画像，并可作为任务候选人 | P0 |
| FR-009 | Agent 分配建议必须参考项目成员画像 | P0 |
| FR-009A | 项目可以通过 Code Context Provider 同步 Repo Inventory 和代码上下文 | P0 |
| FR-009B | 平台 Agent 可以基于 Intent 和 Repo Inventory 生成 Context Plan | P0 |

### 5.2 Intent、Design、Spec 和 Build Plan

| 编号 | 需求 | 优先级 |
|---|---|---|
| FR-010 | Member 可以创建 Intent | P0 |
| FR-011 | 平台可以基于 Intent 和 Code Context 异步生成 Design 建议 | P0 |
| FR-012 | 创建者可以修改并保存 Design 新版本 | P0 |
| FR-013 | 创建者确认 Design 后才可以生成 Spec | P0 |
| FR-014 | 平台可以异步生成 Spec 草稿 | P0 |
| FR-015 | 创建者可以修改并确认 Spec | P0 |
| FR-016 | 平台可以异步生成 Build Plan | P0 |
| FR-017 | Build Plan 必须包含任务、范围和验收标准 | P0 |
| FR-017A | Intent 必须声明为 `ARCHITECTURE`、`FEATURE` 或 `CHANGE` | P0 |
| FR-017B | Architecture Build Plan 不得产生开发分工和 TaskAssignment | P0 |
| FR-017C | Feature/Change Build Plan 必须包含 AI 分工建议及人数、理由、工作量依据和风险 | P0 |
| FR-017D | Design、Spec 和 Build Plan 必须记录使用的 Code Context 版本和关键代码证据 | P0 |
| FR-017E | Git Provider 不承担语义相关性判断，相关文件选择必须由 Context Plan 引导 | P0 |
| FR-018 | Leader 可以修改任务、分工人数、任务拆分和负责人 | P0 |
| FR-019 | 只有 Leader 批准的 Build Plan 才能创建 Task | P0 |
| FR-019A | Leader 可以将任务分配给自己 | P0 |

### 5.3 任务和看板

| 编号 | 需求 | 优先级 |
|---|---|---|
| FR-020 | 平台可以根据批准计划创建 Task | P0 |
| FR-021 | Task 包含目标、范围、非目标、验收标准和测试命令 | P0 |
| FR-022 | Leader 可以分配和重新分配 Task | P0 |
| FR-023 | Member 只能操作分配给自己的 Task | P0 |
| FR-024 | 平台提供工作流级任务看板 | P0 |
| FR-025 | 看板按 Task 状态分列 | P0 |
| FR-026 | Member 可以报告阻塞 | P0 |
| FR-027 | Leader 可以处理阻塞并触发任务包更新 | P0 |

### 5.4 本地 Agent 交接

| 编号 | 需求 | 优先级 |
|---|---|---|
| FR-030 | 平台生成版本化 Markdown 任务包 | P0 |
| FR-031 | 平台生成机器可校验的 JSON 任务包 | P0 |
| FR-032 | 任务包包含设计、规格、计划和代码基线版本 | P0 |
| FR-032A | 任务包包含 `codeContextVersionId`、`baseCommitSha` 和相关文件证据摘要 | P0 |
| FR-033 | 任务包明确允许和禁止的 Git 操作 | P0 |
| FR-034 | Member 可以复制或下载任务包 | P0 |
| FR-035 | 成员开始开发和提交交付时必须确认当前任务包版本 | P0 |
| FR-036 | 任务包过期后不能无提示地继续提交交付 | P0 |

### 5.5 Git、PR 和 CI

| 编号 | 需求 | 优先级 |
|---|---|---|
| FR-040 | Member 可以登记任务分支和 Commit | P0 |
| FR-041 | 平台通过 Git API 校验 Commit 属于目标仓库 | P0 |
| FR-042 | Member 可以登记或创建 PR | P0 |
| FR-043 | 平台校验 PR、head branch 和 head SHA | P0 |
| FR-044 | 平台可以接收 Git/CI Webhook | P0 |
| FR-045 | 平台可以轮询 Git/CI 状态作为兜底 | P0 |
| FR-046 | CI 状态必须绑定当前 Commit SHA | P0 |
| FR-047 | `CI_REQUIRED` 项目中，当前 Commit 的必要 CI 未通过时不能完成 Task 或 Workflow | P0 |
| FR-047A | `CI_BOOTSTRAP` Workflow 必须验证 CI 配置已被 Provider 识别且至少一次引导检查通过 | P0 |
| FR-047B | CI 尚未配置时，普通 Feature/Change 不能绕过 Bootstrap 直接关闭 | P0 |

### 5.6 审计和通知

| 编号 | 需求 | 优先级 |
|---|---|---|
| FR-050 | 关键用户、审批、分配和交付操作必须审计 | P0 |
| FR-051 | AgentRun、GitOperation 和 CIRun 必须保留结果 | P0 |
| FR-052 | 任务包过期和阻塞变更生成站内通知 | P1 |
| FR-053 | Member 可以查看与自己相关的通知 | P1 |
| FR-054 | 成员可以查看和更新自己的项目能力画像 | P0 |

## 6. 非功能需求

### 6.1 安全

- 密码使用 BCrypt；
- JWT Secret、Agent API Key、Git Token 和 Webhook Secret 不入库；
- 资源访问必须进行项目级权限校验；
- Agent 分配输入必须使用当前项目成员画像；
- Agent 文档生成输入必须包含可追溯 Code Context，除非 Leader 明确选择降级且系统记录原因；
- Agent 分工建议必须区分 Intent 层级，不能为 Architecture 生成开发分工；
- Feature/Change 的分工建议必须记录成员画像和当前工作量快照；
- 任务分配必须保存当时使用的画像快照；
- Webhook 必须验签并做事件幂等；
- 审计信息必须脱敏；
- 平台不保存成员本地 Agent Key；
- Leader 不能手工伪造 CI 通过。

### 6.2 一致性

- 状态转换必须在事务中校验；
- 关键实体使用乐观锁；
- 文档和任务包使用不可变版本；
- Code Context 使用版本化记录，Design/Spec/Plan 绑定具体上下文版本；
- 交付与 CI 必须绑定 Commit SHA；
- 重复请求必须具备幂等行为。

### 6.3 可用性

- Agent、Git 和 CI 调用不阻塞 HTTP 请求；
- 外部网络失败时保留 `PENDING` 或 `UNKNOWN`，不能误判为成功；
- 失败任务可查看错误并重试；
- Webhook 丢失时可通过轮询恢复。

## 7. 产品级完成定义

一个处于 `CI_REQUIRED` 项目的普通 Feature/Change Workflow 只有同时满足以下条件才能关闭：

1. 所有必要 Task 已完成；
2. 每个 Task 有有效的 Commit 和 PR；
3. 每个当前交付 Commit 的必要 CI 检查均通过；
4. 没有未解决的 Blocker；
5. Leader 执行关闭操作；
6. 关闭动作写入审计日志。

### 7.1 从零项目的工程与 CI Bootstrap 完成定义

新项目创建时：

```text
Project.ci_status = CI_NOT_CONFIGURED
```

Leader 必须通过专用入口创建一个 `FEATURE + CI_BOOTSTRAP` Workflow，用于提交最小可运行的工程骨架、构建/测试入口和第一条 CI 配置。Bootstrap 不要求项目在 Workflow 开始前已经存在代码框架或 CI，但关闭前必须满足：

1. Bootstrap 交付包含有效 Commit 和 PR；
2. 当前 Commit 中存在项目配置的 CI 文件或等价配置；
3. CI Provider 已识别该配置；
4. 至少一个由该配置触发的引导检查在当前 Commit 上成功；
5. Leader 确认后，系统将项目切换为 `CI_REQUIRED`；
6. Bootstrap 不通过时只能修复、返工或报告阻塞，不能手工标记成功。

因此，Bootstrap 同时解决从空仓库建立最小工程入口，以及“先有 CI 才能验证 CI”的循环：第一条 CI 由 Bootstrap 交付建立，之后再由它自己验证。若仓库已有框架代码，可以只补齐缺失的构建/测试入口和 CI。若 Provider 不能在 PR/head SHA 上运行配置，则必须使用该 Provider 支持的手动触发、分支推送或合并后的首次运行完成验证，平台不能伪造通过结果。

MVP 不提供永久性的“无 CI 关闭”模式。若项目确实不适用 CI，应在项目创建前选择不纳入本 MVP 的其他交付治理方案，而不是让普通 Workflow 绕过门禁。
