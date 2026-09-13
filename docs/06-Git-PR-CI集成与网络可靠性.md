# Git、PR、CI 集成与网络可靠性

## 1. MVP 范围

MVP 优先支持 GitHub + GitHub Actions。Git Provider 和 CI Provider 使用接口抽象，但不在第一版同时实现多个平台。

平台只保存和验证 Git/CI 元数据，不修改成员本地仓库，不代替成员执行 Git CLI。

当前生产 GitHub Git/Actions Adapter 已接入，使用 `GIT_API_URL` 和 `GIT_TOKEN` 环境变量；Mock Git/CI 仅在 `test` 或 `mock-provider` Profile 加载。Agent 文档生成的 Mock Provider 也仅在这两个 Profile 加载，生产环境未配置真实 Agent Adapter 时会显式返回配置错误。

Git Provider 同时为 Code Context 机制提供仓库事实，但它只负责读取 tree、file、diff、commit 等原始数据，不负责判断哪些文件与 Intent 有关。相关性判断由平台 Agent 生成 Context Plan 后交给 Code Context Orchestrator 执行。

## 1.1 从零项目的 CI Bootstrap

项目创建时可以没有任何 CI 配置：

```text
Project.ci_status = CI_NOT_CONFIGURED
```

这时不能让普通 Feature/Change 永久绕过 CI，而应先建立一个固定为 Feature 的 `CI_BOOTSTRAP` Workflow。它的任务固定初始分配给创建该 Workflow 的项目 Leader，由该 Leader 按任务包提交最小工程骨架、构建/测试入口和第一条 CI 配置。Bootstrap 的交付可以在“尚未有代码框架或 CI”的前提下开始，但关闭前必须由新配置自身完成一次有效验证：

```text
bootstrapCommit 存在且属于目标仓库
AND CI 配置存在于 bootstrapCommit
AND Provider 已识别该配置
AND 至少一个真实引导检查以 bootstrapCommit 为 head SHA 并 PASSED
```

这里的“真实引导检查”必须由 GitHub Actions 等 Provider 返回，不能由成员在 Final Report 中声称，也不能由 Leader 手工写入 `PASSED`。检查可以是最小的依赖安装、编译或测试命令，但不能只是平台内部的人工标记。

Bootstrap 成功后，平台将项目切换为：

```text
Project.ci_status = CI_REQUIRED
```

`CI_BOOTSTRAP` 到此即完成其一次性职责。已有工程框架但缺少 CI 的项目可以缩小任务范围，只补齐构建/测试入口和 CI；后续修改 CI 配置属于普通 Change Workflow，并继续受当前 Commit 的 CI 门禁约束。

此后普通 Feature/Change 必须满足当前 Commit 的必要 CI 才能完成。一个 Architecture Workflow 没有代码交付，因此可以不依赖 CI。

若 Provider 只会在默认分支或合并后运行新配置，平台应使用 Provider 支持的分支推送、手动触发或首次合并后的运行完成 Bootstrap 验证，并明确保存实际验证的 Commit SHA。不能为了满足门禁而虚构 PR CI 结果。

## 2. 本地交付流程

```text
任务包提供 branchName 和 baseCommit
  -> 本地 Agent 检查仓库和基线
  -> 创建任务分支
  -> 修改代码并运行测试
  -> 检查 Diff
  -> Commit
  -> Push 任务分支
  -> 创建或登记 PR
  -> 平台校验交付信息
  -> GitHub Actions 运行
  -> 平台同步 CI
```

Workflow 在 Intent 创建阶段保存 pull_request_required，默认开启。策略开启时，本地 Agent 必须创建并登记 Pull Request；策略关闭时可以只 Push 任务分支并等待分支 CI。两种模式都必须提交任务分支和 Commit，且当前 Commit 的 CI 仍是完成条件。

## 3. GitHub 数据获取

平台适配器至少需要以下能力：

```text
getRepository(owner, repo)
getBranch(owner, repo, branch)
getCommit(owner, repo, sha)
getTree(owner, repo, sha)
getFile(owner, repo, sha, path)
compareCommits(owner, repo, baseSha, headSha)
getPullRequest(owner, repo, number)
listCommitCheckRuns(owner, repo, ref)
listWorkflowRuns(owner, repo, filters)
```

平台通过这些接口验证：

- 仓库地址和项目配置一致；
- Commit 存在且属于目标仓库；
- 分支存在；
- PR 属于目标仓库；
- PR head branch 与任务分支一致；
- PR head SHA 与提交的 Commit SHA 一致；
- CI Run 的 head SHA 与当前交付 SHA 一致。

Git Provider 的校验结果必须返回实际解析到的 Commit SHA，以及存在 PR 时的 PR head SHA。平台不能只保存一个“匹配”布尔值；服务端必须把这些观测值与 TaskDelivery 的 `commit_sha` 比较并保存到 GitOperation，完全一致后才能创建同一 SHA 的 CIRun。

用于 Code Context 时，Git Provider 还需要支持：

- 首次同步完整目录树和文件元数据；
- 读取白名单配置文件、README、迁移文件和测试入口；
- 按 Context Plan 读取指定文件或目录下候选文件；
- 按查询词在已同步 tree 中筛选候选路径；
- 读取 base/head Diff；
- 跳过二进制、大文件和敏感路径。

Git Provider 不输出“相关文件推荐”。它只按请求返回事实，避免把语义判断散落在 Provider 适配器中。

参考：

- [GitHub Commits REST API](https://docs.github.com/en/rest/commits/commits)
- [GitHub Pull Requests REST API](https://docs.github.com/en/rest/pulls/pulls)
- [GitHub Check Runs REST API](https://docs.github.com/en/rest/checks/runs)
- [GitHub Actions Workflow Runs REST API](https://docs.github.com/en/rest/actions/workflow-runs)

## 4. Webhook

订阅事件：

```text
push
pull_request
workflow_run
check_run
```

Webhook 接收流程：

1. 读取原始请求体；
2. 使用配置的 Secret 验证签名；
3. 读取 Provider 事件类型；
4. 使用 delivery ID 去重；
5. 保存脱敏的 `WebhookEvent`（事件元数据和 payload hash，不保存完整 payload）；
6. 快速返回 2xx；
7. 后台异步处理事件；
8. 根据 Commit SHA 关联 Task 和 CIRun。

不能仅凭 URL 或请求来源 IP 接受 Webhook。GitHub Webhook 应校验 `X-Hub-Signature-256`，并使用 delivery ID 做幂等。参考：[GitHub Webhook 文档](https://docs.github.com/en/webhooks/using-webhooks/validating-webhook-deliveries)。

## 5. 轮询兜底

Webhook 不是唯一事实来源。后台 Worker 对以下对象进行轮询：

- 有交付记录但没有 CI 记录的 Task；
- `PENDING/RUNNING/UNKNOWN` 的 CIRun；
- 近期有更新的 PR；
- 最近收到过不完整 Webhook 的交付。

轮询必须支持：

- 超时；
- 指数退避；
- 最大重试；
- API 限流处理；
- ETag 或条件请求；
- 手动“立即同步”；
- 最近同步时间和错误信息展示。

## 6. 国内公网部署的可靠性策略

### 6.1 默认策略

MVP 默认使用：

```text
Webhook 实时更新 + 国内平台主动轮询兜底 + 页面手动同步
```

如果部署环境访问外网不稳定：

- 外部调用统一经过 Client 和 OutboxJob；
- 失败保留 `PENDING` 或 `UNKNOWN`；
- 记录最后成功同步时间；
- 允许手动重试；
- 不能把网络失败解释为 CI 通过；
- 对连续失败提供“外部服务不可用”提示。

### 6.2 Relay 方案

如果 GitHub 无法稳定访问平台 Webhook 入口，可增加一个可选的海外 Relay：

```text
GitHub Webhook
  -> 海外 Relay 验签并持久化
  -> Relay 重试投递
  -> 国内平台接收或主动拉取事件
```

Relay 不是 MVP 必须组件，但接口应保留扩展空间。Relay 需要：

- 验证 GitHub 签名；
- 持久化原始事件摘要；
- 使用 delivery ID 去重；
- 与国内平台使用独立内部签名；
- 支持重试和失败告警。

### 6.3 部署区域

如果 GitHub 是主要目标平台，优先考虑将同步组件部署在可稳定访问 GitHub 的网络环境，或将 Git/CI Adapter 与主应用分离部署。MVP 主业务数据库仍只保留一份事实记录。

## 7. CI 状态模型

每个 CIRun 必须保存：

```text
projectId
workflowId
taskId
deliveryId
commitSha
externalId
status
conclusion
detailsUrl
startedAt
finishedAt
lastSyncedAt
configurationPresent
configurationRecognized
```

### 7.1 完成判断

```text
currentDelivery.commitSha == ciRun.headSha
AND requiredChecks all PASSED
AND no requiredCheck FAILED
AND no open TaskBlocker
```

否则不能进入 `DONE`。

对 `CI_BOOTSTRAP` Workflow，完成判断还必须增加：

```text
project.ciStatus == CI_NOT_CONFIGURED
AND bootstrapCommit 包含 CI 配置
AND Provider 已识别配置
AND 至少一个 bootstrap CI check 以 bootstrapCommit 为 head SHA 且 PASSED
```

Bootstrap 关闭与项目状态切换必须在同一事务中完成；切换成功后不得再次创建第二个 Bootstrap 作为普通交付的替代品。

### 7.2 新 Commit

如果同一任务分支产生新的 Commit：

```text
旧 CI 结果保留为历史
当前交付 Commit 更新为新 SHA
任务回到 DELIVERY_SUBMITTED 或 CI_RUNNING
等待新 SHA 的 CI
```

不能使用旧 Commit 的成功结果完成新 Commit。

## 8. Git Token

MVP 建议平台使用项目级最小权限 Token：

- 不返回前端；
- 不写入审计详情；
- 不传给本地 Agent；
- 使用环境变量或密钥管理服务；
- 定期轮换。

由于成员本地负责创建分支、Push 和 PR，平台 Token 初期可以优先使用读取和状态查询权限。若后续平台自动创建 PR，再增加明确的写权限。

## 9. 异常状态

| 情况 | 平台状态 |
|---|---|
| Git API 暂时不可达 | GitOperation 保持 `PENDING` 或转为 `FAILED`；Provider 错误保留可重试原因 |
| Webhook 验签失败 | 丢弃并记录安全日志 |
| Webhook 重复 | 忽略业务重复处理 |
| Commit 不存在 | 交付校验失败 |
| PR SHA 不匹配 | 交付校验失败 |
| CI 仍未开始 | `PENDING` |
| CI 执行中 | `RUNNING` |
| CI 失败 | `FAILED`，任务可返工 |
| CI 通过旧 SHA | 不满足完成条件 |
