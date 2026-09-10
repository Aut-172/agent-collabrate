# Code Context Provider 与代码上下文机制

## 1. 问题

平台 Agent 如果只读取用户提交的 Intent，就无法可靠生成 Design、Spec 和 Build Plan。它缺少当前仓库的事实依据，例如：

- 技术栈和运行方式；
- 目录结构和模块边界；
- 已有接口、实体、状态机和数据库迁移；
- 当前默认分支、最近 Commit 和相关 Diff；
- 既有测试方式和代码风格；
- 与本次 Intent 相关的真实文件和约束。

因此，平台 Agent 在生成正式文档前，必须先获得可追溯的代码上下文证据。

## 2. 决策

引入 `CodeContextProvider` 抽象，并在平台内增加 `CodeContextOrchestrator`。二者不是代码执行器，也不是替代平台 Agent 的另一个设计 Agent。

必须区分三件事：

| 概念 | 职责 |
|---|---|
| Git Provider | 读取远程仓库原始事实：tree、file、diff、commit、PR、CI 状态 |
| Repo Inventory | 保存仓库全量/增量索引：路径、文件类型、大小、hash、配置文件、模块摘要 |
| Code Context Orchestrator | 编排多轮上下文获取：让平台 Agent 规划要读什么，再调用 Provider 取证 |

Git Provider 不能自主判断哪些文件和 Intent 有关。相关性判断属于平台 Agent 的上下文规划能力，并由 Orchestrator 控制轮次、预算和证据格式。

```text
Intent
  -> Repo Inventory
  -> Context Planning Agent 生成 Context Plan
  -> Code Context Orchestrator 调 Provider 多轮取证
  -> Code Evidence / Code Context Bundle
  -> 平台 Agent 生成 Design / Spec / Build Plan
  -> Leader 审批
  -> Task / TaskPackage
```

职责边界：

| 组件 | 职责 |
|---|---|
| Code Context Orchestrator | 管理上下文同步、规划、取证、预算、轮次和版本 |
| Code Context Provider | 按请求读取仓库事实，不做最终语义判断 |
| Agent Provider | 基于 Intent、代码证据、成员画像和工作量生成正式 Design/Spec/Plan |
| Git Provider | 提供远程仓库 tree、file、diff、commit、PR 和状态事实 |
| Local Agent Provider | 未来可选，读取本地仓库和未提交改动，返回代码证据 |
| 本地开发 Agent | 按任务包修改代码、测试、Commit、Push |

平台 Agent 不能只凭 Intent 生成 Design/Spec；也不应把本地 Agent 的自由文本设计稿直接当作正式文档。正式文档必须由平台统一生成、校验、版本化和审批。

## 3. 推荐路线

MVP 当前扩展优先实现 Git Provider + Repo Inventory + 受控多轮取证的轻量版：

```text
Repo Ingestion Worker
  -> 读取默认分支最新 Commit
  -> 全量读取目录树和文件元数据
  -> 读取白名单配置文件、README、迁移、测试入口
  -> 保存 Repo Inventory

Context Planning Agent
  -> 读取 Intent + Repo Inventory
  -> 输出 Context Plan

Code Context Orchestrator
  -> 按 Context Plan 调用 Git Provider 读取文件和 Diff
  -> 如上下文不足，最多追加 1-2 轮补充取证
  -> 生成 Code Evidence Bundle

Document Agent
  -> 平台 Agent 使用证据生成 Design/Spec/Plan
```

暂不在当前 MVP 扩展中实现完整代码 RAG、embedding 索引和本地 CLI 自动调用。接口上预留不同 Provider：

```text
CodeContextProvider
  - GitCodeContextProvider
  - LocalAgentCodeContextProvider
```

未来当 GitHub 访问、私有仓库或本地未提交变更成为核心痛点时，再实现 `LocalAgentCodeContextProvider`。即便如此，本地 Agent 也只返回 Code Evidence，不直接决定正式 Design/Spec/Plan。

## 4. 为什么不只用摘要快照

摘要快照只能解决“完全没有上下文”的问题，不能稳定支持代码相关 Design。它容易丢失：

- 真实类名、方法名、字段名；
- 迁移文件和实体之间的不一致；
- 状态机约束；
- 测试入口和 Mock 约定；
- 最近修改引入的新边界。

因此，Code Context 不能只是大段项目摘要。它应该包含：

1. 基础项目画像；
2. 当前 Commit 和分支事实；
3. Repo Inventory；
4. Context Plan；
5. 与 Intent 相关的文件列表；
6. 相关文件的片段或摘要；
7. 最近 Diff；
8. 风险和未知点；
9. 证据引用。

## 5. 多轮上下文规划

### 5.1 为什么必须多轮

Git Provider 初次读取仓库时并不知道 Intent，因此只能建立通用 Repo Inventory。Intent 出现后，平台 Agent 才能基于语义判断应该读取哪些目录、文件、符号和 Diff。

因此流程必须是多轮的：

```text
第一轮：仓库事实同步
  Git Provider -> tree / metadata / whitelist files -> Repo Inventory

第二轮：上下文规划
  Platform Agent -> Intent + Repo Inventory -> Context Plan

第三轮：定向取证
  Orchestrator -> Git Provider -> selected files / diff / excerpts

第四轮：充分性判断
  Platform Agent -> 判断上下文是否足够
    -> 足够：生成 Design/Spec/Plan
    -> 不足：请求补充取证
```

当前 MVP 扩展可以把第四轮限制为最多一次补充。不能无限循环。

### 5.2 Context Plan

Context Plan 是平台 Agent 在正式生成 Design 前产生的中间结构。

```json
{
  "intentLevel": "FEATURE",
  "readTargets": {
    "files": [
      {
        "path": "src/main/java/com/example/agentcollab/service/TaskDeliveryService.java",
        "reason": "Intent 涉及任务交付状态流转"
      }
    ],
    "directories": [
      {
        "path": "src/main/java/com/example/agentcollab/service",
        "reason": "需要识别工作流推进服务"
      }
    ],
    "searchQueries": ["TaskDelivery", "WorkflowStatus", "OutboxJob"]
  },
  "expectedEvidence": [
    "相关状态机约束",
    "相关 API 入口",
    "相关数据库迁移"
  ],
  "uncertainties": [
    "尚未确认 CI 状态是否由独立 worker 推进"
  ]
}
```

Context Plan 不是正式文档，不需要 Leader 审批，但必须保存在 AgentRun 或 Code Context 运行记录中，便于追溯平台 Agent 为什么读取这些文件。

### 5.3 轮次和预算

为避免上下文规划失控，当前 MVP 扩展使用固定预算：

```text
maxContextRounds = 2
maxFilesPerRound = 20
maxBytesPerFile = 200KB
maxTotalEvidenceBytes = 配置化
skipBinary = true
skipSensitivePaths = true
```

如果达到预算仍不足，平台 Agent 必须输出不确定性，不能伪装成已完整理解代码。

## 6. Code Evidence 结构

建议平台 Agent 接收如下结构化输入：

```json
{
  "provider": "GIT",
  "projectId": 1,
  "baseCommitSha": "abc123",
  "branchName": "main",
  "repositoryUrl": "https://github.com/example/repo",
  "inventoryVersionId": 9,
  "contextPlanId": 18,
  "intentHints": {
    "level": "FEATURE",
    "keywords": ["task delivery", "ci"]
  },
  "repositoryProfile": {
    "techStack": ["Java", "Spring Boot", "PostgreSQL"],
    "buildTools": ["Maven"],
    "testFrameworks": ["JUnit 5", "Testcontainers"],
    "entryPoints": ["src/main/java/..."],
    "migrationPath": "src/main/resources/db/migration"
  },
  "relatedFiles": [
    {
      "path": "src/main/java/com/example/agentcollab/service/TaskDeliveryService.java",
      "reason": "交付流程入口",
      "summary": "负责校验任务包、Final Report、分支和 Commit，并创建 GitOperation",
      "importantSymbols": ["submit", "requireCurrentPackage", "validateReport"],
      "evidenceType": "SOURCE_FILE"
    }
  ],
  "recentDiffs": [
    {
      "path": "src/main/java/com/example/agentcollab/service/CiSyncService.java",
      "changeSummary": "新增 CI 轮询和 SHA 校验"
    }
  ],
  "constraints": [
    "TaskDelivery 的 Commit SHA 必须由 Git Provider 校验",
    "CI 状态必须绑定当前 Commit"
  ],
  "risks": [
    "Provider 网络失败时不能推进成功状态"
  ],
  "openQuestions": []
}
```

正式 Design/Spec/Plan 必须记录使用了哪个 Code Context 版本，以及关键证据引用。

## 7. 代码上下文生命周期

### 7.1 项目级上下文

项目绑定仓库后，平台可以异步建立项目级上下文：

```text
repository_profile
repo_inventory_version
repo_file_index
repo_commit_delta
```

首次同步需要全量读取目录树和文件元数据，但不是把所有文件内容都塞给模型。默认分支出现新 Commit 时，系统只拉取新 Commit 的 tree/diff，并增量更新 inventory、hash 和摘要。

### 7.2 Workflow 级上下文

每个 Workflow 在生成 Design 前应绑定一个 `code_context_version_id`。

如果仓库尚未同步成功：

- 可以创建 Intent；
- 可以等待上下文同步；
- 不能生成正式 Design/Spec/Plan；
- 可以允许 Leader 手动触发同步或选择降级模式。

### 7.3 TaskPackage 级上下文

TaskPackage 必须包含：

```text
baseCommitSha
codeContextVersionId
contextPlanId
relatedFiles
designVersion
specVersion
planVersion
packageVersion
packageHash
```

成员本地 Agent 开发前，应检查：

```text
当前本地分支是否基于 baseCommitSha
任务包是否仍为 CURRENT
相关文件是否仍存在
```

如果本地实际代码与任务包上下文明显冲突，应停止开发并提交 Blocker。

成员提交 Final Report 时，可以回传 `codeContextVersionId`、`contextPlanId` 和 `baseCommitSha` 作为一组追溯字段。平台必须与不可变 TaskPackage 校验一致，并由服务端把任务包中的值固化到 TaskDelivery。`baseCommitSha` 只表示开发基线；实际交付和 CI 完成条件仍分别绑定 TaskDelivery 的 `commitSha` 和 CI Provider 返回的相同 `headSha`，不能使用 Final Report 声明或旧 Commit 的 CI 结果替代。

## 8. API 建议

```text
GET  /api/projects/{id}/code-context/latest
POST /api/projects/{id}/code-context/sync
GET  /api/projects/{id}/repo-inventory/latest
GET  /api/projects/{id}/code-context/runs/{runId}
GET  /api/workflows/{id}/code-context
POST /api/workflows/{id}/code-context/refresh
```

生成 Design / Spec / Build Plan 时，服务端内部应先解析可用的 Code Context：

```text
POST /api/workflows/{id}/generate-design
  -> require Repo Inventory
  -> generate Context Plan
  -> fetch Code Evidence
  -> require CodeContext CURRENT
  -> create AgentRun

POST /api/workflows/{id}/generate-spec
  -> use confirmed Design + CodeContext

POST /api/workflows/{id}/generate-build-plan
  -> use confirmed Design/Spec + CodeContext + member profiles + workload
```

## 9. 过期规则

Code Context 进入 `STALE` 的常见情况：

- 默认分支最新 Commit 变化；
- Git Provider 返回仓库 tree 与当前记录不一致；
- Leader 手动要求刷新；
- Design/Spec/Plan 被修改后需要重新计算相关上下文；
- TaskPackage 引用的 `baseCommitSha` 不再是推荐开发基线。

当前 MVP 扩展可以采用简单策略：

```text
如果 latestDefaultBranchCommit != codeContext.baseCommitSha
则标记 Code Context 为 STALE
并提示 Leader 刷新或继续使用旧上下文
```

继续使用旧上下文必须在 AgentRun 中记录原因，避免后续无法追溯。

## 10. Git Provider 轻量实现范围

第一版只需要支持：

- 解析仓库 owner/repo；
- 读取默认分支；
- 读取最新 Commit SHA；
- 读取目录树；
- 读取白名单路径文件内容；
- 按 Context Plan 读取指定文件内容；
- 按查询词返回候选路径；
- 读取两个 Commit 之间的 Diff；
- 识别配置文件、迁移文件、测试目录和主要源码目录；
- 保存 provider 原始错误和同步状态。

白名单路径建议：

```text
README*
pom.xml / build.gradle / package.json
src/main/**
src/test/**
src/main/resources/db/migration/**
.github/workflows/**
docs/**
```

大文件、二进制文件、密钥文件和敏感路径必须跳过。

Git Provider 只返回请求到的仓库事实，不能自己决定“这些文件与 Intent 有关”。相关性判断由 Context Planning Agent 和 Orchestrator 完成。

## 11. 网络和降级

GitHub 等外部 Provider 不稳定时：

- Code Context 同步任务保持 `PENDING` 或 `FAILED`；
- Design/Spec/Plan 不应静默降级为无代码上下文生成；
- Leader 可以手动重试；
- 后续可支持上传 zip/patch 或 Local Agent Provider 作为降级；
- 所有失败都必须可见，不得伪造代码证据。

## 12. Local Agent Provider 的未来边界

Local Agent Provider 可以在未来解决：

- 私有仓库不能让公网平台读取；
- GitHub/GitLab 网络不可用；
- 需要读取未提交本地改动；
- 需要运行本地测试或静态分析。

但它不应替代平台 Agent 的正式文档职责。它返回的是 Code Evidence，不是最终 Design/Spec/Plan。

未来流程：

```text
平台生成 Code Context Request / Context Plan
  -> 本地 Connector 调用 Codex CLI
  -> Codex CLI 读取仓库并返回 Code Evidence
  -> 平台校验格式、保存版本
  -> 平台 Agent 生成正式文档
```

平台不保存成员本地 Agent Key。本地 Connector 的安装、授权、任务确认和输出脱敏需要单独设计，不纳入当前 MVP 扩展。

## 13. 验收标准

当前 MVP 扩展完成时至少满足：

1. 项目可以触发 Git Repo Inventory 同步；
2. 系统保存当前默认分支 Commit、目录树、文件元数据和白名单文件摘要；
3. Design 生成前先生成并保存 Context Plan；
4. Orchestrator 按 Context Plan 拉取定向文件证据；
5. 上下文不足时最多进行受控补充轮次；
6. Design 生成时必须绑定某个 Code Context 版本；
7. AgentRun 请求摘要记录使用的 `code_context_version_id` 和 Context Plan；
8. Design/Spec/Plan 文档中保留关键证据引用；
9. 默认分支 Commit 变化后旧上下文可被识别为 `STALE`；
10. Git Provider 失败不会生成“无证据但看似正常”的 Design；
11. Git Provider 不包含自主相关性判断逻辑；
12. 任务包包含 `baseCommitSha`、`codeContextVersionId`、`contextPlanId` 和代码证据摘要。
