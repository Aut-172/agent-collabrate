# MVP 验收测试矩阵

## 1. 验收原则

- 每条 P0 需求至少有一个可执行验证；
- 状态、权限、版本和外部事实优先使用自动化测试；
- 外部 Provider 使用 Mock，不依赖真实网络；
- 测试必须验证失败路径，不能只验证主流程；
- 当前 Commit 和 CI SHA 的一致性是完成判断的强制条件。

## 2. 产品验收矩阵

| 编号 | 验收条件 | 验证方式 |
|---|---|---|
| AT-001 | 首个 Leader 可以初始化 | 集成测试/部署测试 |
| AT-002 | Member 不能自行注册为 Leader | 权限测试 |
| AT-003 | Leader 可以创建项目并添加成员 | API 集成测试 |
| AT-004 | Member 只能访问所属项目 | 越权测试 |
| AT-004A | 成员加入项目后必须填写能力画像 | API/业务规则测试 |
| AT-004B | Leader 也必须填写能力画像并可成为任务负责人 | 任务分配集成测试 |
| AT-004C | Agent 分配建议使用项目成员画像 | Agent Prompt/Plan 测试 |
| AT-004D | Architecture 不生成开发分工、负责人或 TaskAssignment | Schema/状态机测试 |
| AT-004E | Feature/Change 生成分工模式、推荐人数、理由、工作量依据和警告 | Agent 输出/Schema 测试 |
| AT-004F | Feature 默认倾向于比 Change 推荐更多成员，但简单 Feature 和复杂 Change 可由 AI 例外裁定并说明理由 | 规则测试 |
| AT-005 | Member 可以创建 Intent | API 测试 |
| AT-006 | Design 生成是异步的并返回 runId | API + Worker 测试 |
| AT-007 | Agent 失败可查看原因并重试 | Worker 集成测试 |
| AT-008 | Design、Spec、Plan 历史版本不被覆盖 | Repository 测试 |
| AT-009 | Spec 未确认不能生成有效 Plan | 状态机测试 |
| AT-010 | Build Plan JSON Schema 不通过时不能批准 | Schema/API 测试 |
| AT-011 | Agent 分配建议可以被 Leader 修改 | Service 测试 |
| AT-011A | 分配记录保存画像快照和分配理由 | Repository/集成测试 |
| AT-011B | 分配记录保存工作量快照、分配评分和画像版本 | Repository/集成测试 |
| AT-011C | 已开始执行的任务不因工作量变化自动换人 | 状态机/服务测试 |
| AT-012 | 未批准 Plan 不能创建 Task | 权限/状态测试 |
| AT-013 | 创建 Task 具有来源版本信息 | 集成测试 |
| AT-014 | Task Package 同时有 Markdown 和 JSON | API 测试 |
| AT-015 | Task Package 包含 baseCommit 和版本信息 | JSON Schema 测试 |
| AT-016 | 任务包更新后旧版本标记 STALE | Service 测试 |
| AT-017 | 过期任务包不能开始开发 | 409 API 测试 |
| AT-018 | 过期任务包不能提交交付 | 409 API 测试 |
| AT-019 | 看板按 Task 状态正确聚合 | 查询测试 |
| AT-020 | Member 只能开始自己负责的 Task | 权限测试 |
| AT-021 | Member 可以提交结构化 Blocker | API 集成测试 |
| AT-022 | Blocker 后 Workflow 不无条件整体阻塞 | 状态机测试 |
| AT-023 | Leader 解决 Blocker 后生成新任务包 | 集成测试 |
| AT-024 | Leader 可以重新分配未完成 Task | Service/API 测试 |
| AT-025 | 重新分配会使旧任务包失效 | Service 测试 |
| AT-026 | Commit 不存在时拒绝交付 | Git Client Mock 测试 |
| AT-027 | 跨仓库 Commit 被拒绝 | Git Client Mock 测试 |
| AT-028 | PR head SHA 不匹配时拒绝交付 | Git Client Mock 测试 |
| AT-028A | Final Report 缺失或 Schema 不合法时拒绝交付 | API/Schema 测试 |
| AT-028B | TaskDelivery 保存任务包版本和报告内容 | 集成测试 |
| AT-028C | Final Report 中声称测试通过但 CI 未通过时不能完成任务 | 状态机测试 |
| AT-029 | Webhook 签名错误不改变业务状态 | Webhook 测试 |
| AT-030 | 重复 Webhook 不重复创建 CIRun | 幂等测试 |
| AT-031 | Git API 不可达时不能误判成功 | 故障测试 |
| AT-032 | CI 运行中不能完成 Task | 状态机测试 |
| AT-033 | CI 通过但对应旧 Commit 时不能完成 Task | 关键规则测试 |
| AT-034 | 当前 Commit 的必要 CI 通过后可以完成 Task | 集成测试 |
| AT-035 | 所有必要 Task 完成后 Workflow 才能关闭 | 状态机测试 |
| AT-036 | 未解决 Blocker 时不能关闭 Workflow | 状态机测试 |
| AT-037 | Leader 不能手工伪造 CI PASSED | 权限/接口测试 |
| AT-038 | 关键动作均有 AuditLog | 审计集成测试 |
| AT-039 | 审计日志不可修改和删除 | API 测试 |
| AT-040 | 敏感凭证不进入日志和响应 | 安全测试 |

## 3. 关键状态机测试

### 正向流程

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

### 必须拒绝的流程

- `INTENT -> DONE`
- `DESIGN_PROPOSED -> PLAN_APPROVED`
- 未确认 Spec 直接创建 Task；
- 未批准 Plan 创建 Task；
- CI `FAILED -> DONE`；
- 旧 SHA 的 CI `PASSED -> DONE`；
- 有 Open Blocker 时 `READY_TO_CLOSE`；
- 非负责人开始或提交 Task；
- 未完成能力画像的成员被分配 Task；
- 非 Leader 批准 Plan；
- 被移除项目成员继续写入资源。

## 4. 外部服务故障测试

模拟：

- Agent 超时；
- Agent 429；
- Agent 5xx；
- Git API DNS/连接失败；
- Git API 限流；
- CI Webhook 延迟；
- Webhook 重复；
- Webhook 签名错误；
- CI 结果缺失；
- Commit 已被新 SHA 替换。

验证：

- 任务不被误标成功；
- 运行记录保留错误；
- 后台任务可重试；
- 页面展示可解释状态；
- 手动同步有效；
- 旧结果不污染新交付。

## 5. 安全测试

- 未登录访问受保护接口返回 401；
- Member 访问其他项目返回 404/403；
- Member 调用 Leader 接口返回 403；
- 客户端传入 role 不会提升权限；
- JWT Secret 不出现在响应和日志；
- Git Token 不出现在任务包；
- Webhook 必须验签；
- SQL/JSON 输入不能绕过资源条件；
- 并发更新使用乐观锁；
- 重复请求不会重复创建任务或任务包。

## 6. Definition of Done

MVP 代码可以进入交付阶段前必须满足：

1. P0 验收条目全部通过；
2. Flyway migration 可以在空数据库执行；
3. PostgreSQL 集成测试通过；
4. 外部 Provider 都有 Mock 测试；
5. OpenAPI 文档与实现一致；
6. 关键状态转换有单元测试；
7. 权限和跨项目隔离有集成测试；
8. CI SHA 绑定规则有测试；
9. 无未解决的高风险安全问题；
10. 文档中的已冻结决策与实现一致。
