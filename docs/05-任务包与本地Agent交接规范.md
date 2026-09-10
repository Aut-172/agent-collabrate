# 任务包与本地 Agent 交接规范

## 1. 设计目标

任务包是平台和成员本地 Agent 之间的正式交接物，不是一段随意 Prompt。

任务包必须同时满足：

- 人可读；
- Agent 可读；
- 平台可校验；
- 版本可追踪；
- 过期可识别；
- 结果可回填。

每个版本包含：

```text
TASK-001.md
TASK-001.json
```

平台可以提供“复制任务指令”和“下载任务包”两个入口。下载任务包是标准方式，复制是便捷方式。

## 2. 任务包的权威边界

| 内容 | 权威来源 |
|---|---|
| 目标、范围、非目标、验收标准 | 当前有效 Task Package |
| 当前代码结构和实现细节 | 成员本地 Git 仓库 |
| 分支、Commit、PR 是否真实存在 | Git Provider |
| 是否通过自动检查 | CI Provider |
| 任务包是否仍有效 | 平台当前版本 |

任务包不包含整个代码仓库。它提供相关路径和规则，要求本地 Agent 先读取实际代码。

## 3. JSON 结构

```json
{
  "schemaVersion": "1.0",
  "task": {
    "taskId": "TASK-001",
    "workflowId": "WF-042",
    "taskVersion": 3,
    "packageId": 3003,
    "packageVersion": 2,
    "packageHash": "sha256:...",
    "status": "CURRENT",
    "generatedAt": "2026-09-09T10:00:00Z"
  },
  "project": {
    "projectId": 7,
    "name": "account-service",
    "repositoryUrl": "https://github.com/example/account-service",
    "defaultBranch": "main"
  },
  "context": {
    "designVersion": 2,
    "specVersion": 3,
    "buildPlanVersion": 1,
    "baseBranch": "main",
    "baseCommit": "abcdef1234567890",
    "relevantPaths": [
      "src/main/java/example/auth",
      "src/test/java/example/auth"
    ]
  },
  "assignee": {
    "userId": 12,
    "projectRole": "LEADER",
    "profileVersion": 2,
    "profileSnapshot": {
      "responsibilities": ["后端架构", "认证模块"],
      "skills": ["Java", "Spring Boot"],
      "preferredTaskTypes": ["后端开发"]
    }
  },
  "objective": "实现用户登录接口。",
  "scope": [
    "添加登录接口",
    "实现密码校验",
    "生成 JWT",
    "补充相关测试"
  ],
  "nonGoals": [
    "不修改注册流程",
    "不实现 Refresh Token"
  ],
  "acceptanceCriteria": [
    "错误密码返回 HTTP 401",
    "用户不存在返回 HTTP 401",
    "成功登录返回有效 JWT",
    "原有测试通过"
  ],
  "verificationCommands": [
    "mvn test"
  ],
  "executionPolicy": {
    "mode": "LOCAL_AGENT",
    "workingDirectory": "repository-root",
    "preflight": {
      "requireRepositoryMatch": true,
      "requireCleanWorkingTree": false,
      "requireBaseCommitCheck": true
    },
    "git": {
      "remote": "origin",
      "targetBranch": "agent/wf-42/task-001",
      "createBranch": true,
      "commit": true,
      "push": true,
      "createPullRequest": "OPTIONAL",
      "merge": false,
      "forcePush": false,
      "deleteRemoteBranch": false
    }
  },
  "allowedPaths": [
    "src/main/java/example/auth/**",
    "src/test/java/example/auth/**"
  ],
  "forbiddenPaths": [
    ".env",
    ".env.*",
    "*.pem",
    "*.key",
    "secrets/**"
  ],
  "deliverables": [
    "修改后的代码",
    "测试结果",
    "Commit SHA",
    "Pull Request URL",
    "未解决问题"
  ]
}
```

## 4. Markdown 模板

```markdown
# Agent Task Package

## Metadata

- Task ID: TASK-001
- Workflow ID: WF-042
- Task version: 3
- Package version: 2
- Design version: 2
- Spec version: 3
- Build plan version: 1
- Base branch: main
- Base commit: abcdef1234567890

## Objective

实现用户登录接口。

## Scope

- 添加登录接口
- 实现密码校验
- 生成 JWT
- 补充相关测试

## Non-goals

- 不修改注册流程
- 不实现 Refresh Token

## Relevant Context

- 先阅读认证模块和现有测试；
- 遵循项目现有错误响应格式；
- 不假设任务包中没有写出的接口已经存在；
- 以本地仓库实际代码为准检查文件和类名。

## Acceptance Criteria

- [ ] 错误密码返回 HTTP 401
- [ ] 用户不存在返回 HTTP 401
- [ ] 成功登录返回有效 JWT
- [ ] 原有测试通过

## Verification

```bash
mvn test
```

## Git Execution Policy

1. 检查仓库地址、远程 `origin`、当前分支和 HEAD。
2. 以 `baseCommit` 为基线创建或切换到目标分支。
3. 允许修改任务范围内的代码和测试。
4. 允许执行 `git add`、`git commit` 和 `git push`。
5. 只允许推送到 `origin/agent/wf-42/task-001`。
6. 禁止 Push 到 `main` 或 `master`。
7. 禁止 `force push`、Merge 和删除远程分支。
8. 创建 PR 之前检查 Diff 和测试结果。
9. 不要读取、提交或输出密钥文件。

## Preflight

开始修改前输出：

- Repository: PASS/FAIL
- Branch: PASS/FAIL
- HEAD/base commit: PASS/FAIL
- Relevant files: PASS/FAIL
- Context conflict: NONE/FOUND
- Can start: YES/NO

## Conflict Rule

如果规格与代码现状冲突、缺少业务决策、任务包过期、需要超出允许范围修改，停止修改并输出阻塞报告。

## Final Report

完成后输出：

- 修改文件；
- 测试命令和结果；
- Commit SHA；
- 分支名；
- PR URL；
- 未解决问题；
- 是否有超出范围的修改。
```

## 5. 本地 Agent 推荐执行顺序

```text
读取任务包
  -> 检查仓库和基线
  -> 检查任务包是否仍为 CURRENT
  -> 检查相关代码和项目约定
  -> 输出 Preflight
  -> 获得成员确认
  -> 创建/切换任务分支
  -> 修改代码和测试
  -> 运行验证命令
  -> 检查 Diff
  -> Commit
  -> Push 任务分支
  -> 可选创建 PR
  -> 输出 Final Report
```

任务包只是声明性指令，不是本地安全沙箱。真正的保护还依赖：

- Git Provider 的默认分支保护；
- 成员账号权限；
- 本地 Agent 的确认策略；
- 不把平台 Git Token 传入本地 Agent；
- 成员检查和评审 Diff。

## 6. Git 操作权限

| 操作 | MVP |
|---|---|
| 查看仓库和分支 | 允许 |
| 创建任务分支 | 允许 |
| 修改代码和测试 | 允许，限任务范围 |
| Commit | 允许 |
| Push 任务分支 | 允许 |
| 创建 PR | 可选 |
| Push 默认分支 | 禁止 |
| Force Push | 禁止 |
| Merge PR | 禁止 |
| 删除远程分支 | 禁止 |
| 读取密钥文件 | 禁止 |

## 7. 版本和过期

Task Package 关联：

```text
taskVersion
packageVersion
designVersion
specVersion
buildPlanVersion
baseCommit
contentHash
```

开始开发和提交交付都必须提交 `packageVersion`。平台发现不是当前版本时返回：

```json
{
  "code": "TASK_PACKAGE_STALE",
  "currentVersion": 3,
  "submittedVersion": 2,
  "latestPackageUrl": "/api/tasks/1001/packages/current",
  "diffUrl": "/api/tasks/1001/packages/diff?from=2&to=3"
}
```

通知用于提醒，版本校验用于保证正确性。平台不依赖实时通知作为唯一保障。

## 8. 阻塞报告格式

```json
{
  "outcome": "BLOCKED",
  "reasonCode": "REQUIREMENT_CLARIFICATION",
  "summary": "规格要求和当前认证模块冲突",
  "evidence": [
    "任务包提到 TokenService，但当前仓库不存在该组件"
  ],
  "currentBranch": "agent/wf-42/task-001",
  "currentCommit": "abcdef1234567890",
  "changesMade": [],
  "question": "是否允许新增 TokenService？"
}
```

成员提交阻塞后，平台创建 TaskBlocker 并通知 Leader。Leader 的回复或上下文修改可能生成新的任务包版本。这里的成员可以是普通 Member，也可以是承担开发任务的 Leader。

## 9. 凭证边界

| 凭证 | 存放位置 | 用途 |
|---|---|---|
| 平台 Agent API Key | 平台服务器 | Design/Spec/Plan 生成 |
| 成员本地 Agent 登录状态或 Key | 成员本机 | 读取和修改本地仓库 |
| 成员 Git 凭证 | 成员本机 | 本地 Push/PR |
| 平台 Git Token | 平台服务器 | 查询和同步 Git/CI |
| Webhook Secret | 平台服务器 | 验证 Webhook |

平台不要求成员在网页填写本地 Agent Key，也不接收或转发成员本地凭证。

## 10. Final Report 交付报告

本地 Agent 完成任务后，必须输出结构化 Final Report。成员需要先审阅报告，再在平台提交交付。

Final Report 的作用：

- 说明本地 Agent 实际修改了什么；
- 记录本地执行过哪些验证命令；
- 说明是否存在未解决问题；
- 帮助 Leader 和后续 Agent 理解交付上下文；
- 作为平台审计和返工判断的辅助证据。

Final Report 不是平台认定完成的唯一依据，也不能替代：

- Git Provider 对 Commit 的真实性校验；
- PR 与 head SHA 的校验；
- CI Provider 对当前 Commit 的检查结果；
- 成员对代码 Diff 的人工审阅。

推荐 JSON 格式：

```json
{
  "schemaVersion": "1.0",
  "taskId": "TASK-001",
  "packageId": 3003,
  "packageVersion": 2,
  "packageHash": "sha256:...",
  "outcome": "READY_FOR_REVIEW",
  "summary": "已实现登录接口并补充认证测试。",
  "changedFiles": [
    "src/main/java/example/auth/LoginController.java",
    "src/test/java/example/auth/LoginControllerTest.java"
  ],
  "tests": [
    {
      "command": "mvn test",
      "status": "PASSED",
      "summary": "全部测试通过"
    }
  ],
  "git": {
    "branchName": "agent/wf-42/task-001",
    "commitSha": "abcdef1234567890",
    "pullRequestUrl": "https://github.com/example/repo/pull/18"
  },
  "acceptanceCriteria": [
    {
      "criterion": "错误密码返回 HTTP 401",
      "status": "PASSED",
      "evidence": "LoginControllerTest.invalidPassword"
    }
  ],
  "unresolvedIssues": [],
  "outOfScopeChanges": [],
  "blockers": []
}
```

允许的 `outcome`：

```text
READY_FOR_REVIEW
BLOCKED
FAILED
```

成员提交交付时必须：

1. 确认当前任务包 ID、版本和哈希；
2. 审阅 Final Report 和本地 Diff；
3. 确认报告中的任务包、Commit、分支和 PR 信息；
4. 将报告和任务包引用提交到平台，不重新上传任务包；
5. 对报告中的内容承担提交责任。

平台收到报告后：

1. 校验报告 Schema；
2. 校验提交人是当前负责人；
3. 校验任务包 ID、版本和哈希仍是 CURRENT；
4. 校验该成员最近确认的任务包与交付引用一致；
5. 校验 Final Report 中的任务包引用一致；
6. 保存状态为 `SUBMITTED` 的交付报告和任务包引用；
7. 创建异步 `GIT_SYNC` 任务；
8. 将 Task 置为 `DELIVERY_SUBMITTED`；
9. 后台通过 Git Provider 校验 Commit、分支和 PR；
10. Git 事实校验成功后，创建或更新当前 Commit SHA 对应的 CI 同步任务。

同步提交阶段只校验平台已有事实和请求一致性，不把 Final Report 中的 Git 字段当作 Provider 事实。所有 Git/PR/CI Provider 调用必须由后台任务异步执行并保留运行记录。

平台不应默认相信报告中的 `tests.status` 或验收标准状态。它们用于说明和审计；最终完成仍由当前 Commit 的 CI 结果决定。

## 11. 内容哈希计算口径

`content_hash` 使用任务包 JSON 的 UTF-8 规范化序列计算 SHA-256。计算时包含 `packageId` 和 `packageVersion`，但排除自引用的 `task.packageHash` 字段；计算完成后再将 `sha256:<hex>` 同时写入 JSON、Markdown 和数据库。服务端生成、任务包确认和交付校验必须使用同一口径。
