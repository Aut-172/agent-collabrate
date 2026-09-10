# AI Agent 协作开发平台 MVP 文档

## 文档用途

这组文档用于指导 MVP 的产品确认、后端开发、前端开发、集成测试和验收。

项目采用以下核心模式：

```text
平台负责需求与任务编排、Agent 文档生成、Git/CI 状态同步和审计
成员在本地使用 Codex CLI 或其他 Agent 完成代码修改、测试和 Git 操作
```

平台不访问成员本地代码，不在服务器执行成员代码，不保存成员本地 Agent Key。

## 推荐阅读顺序

1. [01-MVP需求分析与PRD.md](01-MVP需求分析与PRD.md)
2. [02-MVP用例清单.md](02-MVP用例清单.md)
3. [03-MVP业务流程与状态机.md](03-MVP业务流程与状态机.md)
4. [04-MVP技术方案.md](04-MVP技术方案.md)
5. [05-任务包与本地Agent交接规范.md](05-任务包与本地Agent交接规范.md)
6. [06-Git-PR-CI集成与网络可靠性.md](06-Git-PR-CI集成与网络可靠性.md)
7. [07-Leader操作与实体影响矩阵.md](07-Leader操作与实体影响矩阵.md)
8. [08-MVP验收测试矩阵.md](08-MVP验收测试矩阵.md)
9. [09-架构决策记录.md](09-架构决策记录.md)
10. [10-数据库表设计.md](10-数据库表设计.md)
11. [11-Vibe-Coding启动指南.md](11-Vibe-Coding启动指南.md)
12. [12-MVP前端设计规范与页面说明.md](12-MVP前端设计规范与页面说明.md)
13. [13-任务包确认与交付流程补充.md](13-任务包确认与交付流程补充.md)
14. [14-Intent层级与AI分工策略.md](14-Intent层级与AI分工策略.md)

## 已冻结的 MVP 决策

| 主题 | 决策 |
|---|---|
| 应用形态 | Java 17+、Spring Boot 3.x 单体应用 |
| 数据库 | PostgreSQL，使用 Flyway 管理结构 |
| Agent 文档生成 | 平台服务端调用 Agent Provider API |
| Agent 代码执行 | 成员本地使用 Codex CLI 或其他 Agent |
| 本地 Agent Key | 只保存在成员本机，不上传平台 |
| 任务分配 | Agent 生成任务和人员分配建议，Leader 修改并批准 |
| 任务交接 | 版本化 Markdown + JSON 任务包，支持复制和下载 |
| 交付确认 | 不重新上传任务包；通过任务包 ID、版本、哈希和确认记录关联交付 |
| 看板 | 纳入 MVP，作为 Task 状态的可视化 |
| 成员画像 | 每个项目成员自填项目职责、能力、经验和限制；Leader 也必须填写并可被分配任务 |
| Git 平台 | MVP 优先支持 GitHub |
| Git 操作 | 本地 Agent 可按任务包执行分支、Commit、Push；禁止 Merge 和 Force Push |
| PR | 成员本地创建或登记 PR，平台校验和同步 |
| CI | GitHub Actions 优先，Webhook 实时更新，轮询兜底 |
| 完成条件 | `CI_REQUIRED` 项目中当前 Commit 对应的必要 CI 检查通过后才允许完成；新项目先通过一次性 CI Bootstrap 建立门禁 |
| 阻塞 | Task 进入 `BLOCKED`，Workflow 默认保持原状态并标记需关注 |
| 版本 | 设计、规格、计划、任务和任务包都不可覆盖，使用新版本 |
| 异步 | Agent、Git、CI 长耗时操作使用数据库任务表和 Worker |
| 权限 | 资源级项目隔离；Leader 治理权限以项目成员关系为准 |
| Intent 分工 | Architecture 不产生开发分工；Feature/Change 必须生成 AI 分工建议，Feature 通常比 Change 推荐更多成员，但由 AI 基于范围、画像和工作量裁定 |

## 文档维护规则

- 产品行为修改先更新 PRD 和用例；
- 状态转换修改先更新状态机文档；
- 数据库、API、异步和部署修改更新技术方案；
- 任务包字段或本地交接流程修改更新任务包规范；
- GitHub、PR、CI、Webhook 和网络策略修改更新集成文档；
- 关键架构取舍写入 ADR；
- 所有新增需求必须同步增加验收测试条目；
- 已批准文档和任务包不直接覆盖，必须产生新版本。
