package com.example.agentcollab.service;

import com.example.agentcollab.domain.*;
import com.example.agentcollab.dto.TaskPackageDtos;
import com.example.agentcollab.exception.ApiException;
import com.example.agentcollab.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class TaskPackageService {
    private final TaskPackageRepository packages;
    private final TaskRepository tasks;
    private final WorkflowRepository workflows;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final TaskAssignmentRepository assignments;
    private final DocumentVersionRepository documents;
    private final CodeContextVersionRepository contexts;
    private final CodeContextPlanRepository contextPlans;
    private final RepoInventoryVersionRepository inventories;
    private final CodeContextFileRepository contextFiles;
    private final TaskBlockerRepository blockers;
    private final WorkflowService workflowService;
    private final ObjectMapper json;
    private final JsonSchema packageSchema;
    private final EntityManager entityManager;
    private final NotificationService notifications;

    public TaskPackageService(TaskPackageRepository packages, TaskRepository tasks, WorkflowRepository workflows,
                              ProjectRepository projects, ProjectMemberRepository members,
                              TaskAssignmentRepository assignments, DocumentVersionRepository documents,
                              CodeContextVersionRepository contexts, CodeContextPlanRepository contextPlans,
                              RepoInventoryVersionRepository inventories, CodeContextFileRepository contextFiles,
                              TaskBlockerRepository blockers, WorkflowService workflowService,
                              ObjectMapper json, EntityManager entityManager,
                              NotificationService notifications) {
        this.packages = packages; this.tasks = tasks; this.workflows = workflows; this.projects = projects;
        this.members = members;
        this.assignments = assignments; this.documents = documents; this.contexts = contexts;
        this.contextPlans = contextPlans; this.inventories = inventories;
        this.contextFiles = contextFiles; this.blockers = blockers;
        this.workflowService = workflowService; this.json = json;
        this.entityManager = entityManager;
        this.notifications = notifications;
        this.packageSchema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(getClass().getResourceAsStream("/schema/task-package-v1.schema.json"));
    }

    @Transactional
    public TaskPackage createInitial(Task task) {
        var existing = packages.findTopByTaskIdOrderByPackageVersionDesc(task.getId());
        if (existing.isPresent()) return existing.orElseThrow();
        return create(task, 1);
    }

    @Transactional
    public TaskPackage regenerate(Task task) {
        TaskPackage current = packages.findByTaskIdAndStatus(task.getId(), TaskPackageStatus.CURRENT)
                .orElseThrow(() -> new IllegalStateException("Current TaskPackage is missing"));
        int nextVersion = packages.findTopByTaskIdOrderByPackageVersionDesc(task.getId())
                .map(value -> value.getPackageVersion() + 1).orElse(1);
        current.markStale();
        packages.saveAndFlush(current);
        TaskPackage replacement = create(task, nextVersion);
        current.supersedeWith(replacement.getId());
        packages.save(current);
        notifyPackageUpdated(task, replacement);
        return replacement;
    }

    private void notifyPackageUpdated(Task task, TaskPackage taskPackage) {
        assignments.findByTaskIdAndCurrentTrue(task.getId()).ifPresent(assignment ->
                notifications.notifyUser(assignment.getAssigneeUserId(), "TASK_PACKAGE_UPDATED", "TASK_PACKAGE",
                        taskPackage.getId(), "任务包已更新",
                        task.getExternalKey() + " 的任务包已更新为 v" + taskPackage.getPackageVersion()));
    }

    private TaskPackage create(Task task, int packageVersion) {
        Long packageId = ((Number) entityManager.createNativeQuery(
                "SELECT nextval('task_packages_id_seq')").getSingleResult()).longValue();
        Workflow workflow = workflows.findById(task.getWorkflowId()).orElseThrow();
        Project project = projects.findById(workflow.getProjectId()).orElseThrow();
        TaskAssignment assignment = assignments.findByTaskIdAndCurrentTrue(task.getId()).orElse(null);
        DocumentVersion plan = sourcePlan(task);
        CodeContextVersion codeContext = requireCurrentContext(workflow, plan);
        JsonNode details = taskDetails(task, plan);
        var root = json.createObjectNode();
        root.put("schemaVersion", "1.0");
        var meta = root.putObject("task");
        meta.put("taskId", task.getExternalKey()); meta.put("workflowId", "WF-" + workflow.getId());
        meta.put("packageId", packageId);
        meta.put("taskVersion", task.getVersion() == null ? 0 : task.getVersion());
        meta.put("packageVersion", packageVersion);
        meta.put("branchName", task.getBranchName());
        meta.put("status", "CURRENT");
        meta.put("generatedAt", java.time.Instant.now().toString());
        root.putObject("project").put("projectId", project.getId()).put("name", project.getName())
                .put("repositoryUrl", project.getRepositoryUrl()).put("defaultBranch", project.getDefaultBranch());
        var context = root.putObject("context");
        context.put("designVersion", latestVersion(task.getWorkflowId(), DocumentType.DESIGN));
        context.put("specVersion", task.getSourceSpecVersion() == null ? 0 : task.getSourceSpecVersion());
        context.put("buildPlanVersion", task.getSourcePlanVersion()); context.put("baseBranch", project.getDefaultBranch());
        context.put("codeContextVersionId", codeContext.getId());
        context.put("contextPlanId", codeContext.getContextPlanId());
        context.put("baseCommitSha", codeContext.getBaseCommitSha());
        context.put("baseCommit", codeContext.getBaseCommitSha());
        var relevantPaths = context.putArray("relevantPaths");
        var codeEvidence = context.putArray("codeEvidence");
        contextFiles.findByContextVersionIdOrderByPath(codeContext.getId()).forEach(file -> {
            relevantPaths.add(file.getPath());
            var evidence = codeEvidence.addObject();
            evidence.put("path", file.getPath());
            evidence.put("reason", file.getSummary());
            evidence.put("summary", "证据类型=" + file.getEvidenceType().name() + "；内容哈希="
                    + (file.getContentHash() == null ? "unknown" : file.getContentHash()));
        });
        if (assignment != null) {
            var a = root.putObject("assignee"); a.put("userId", assignment.getAssigneeUserId());
            members.findByProjectIdAndUserId(workflow.getProjectId(), assignment.getAssigneeUserId())
                    .ifPresent(member -> a.put("projectRole", member.getProjectRole().name()));
            a.put("profileVersion", assignment.getProfileVersion()); a.set("profileSnapshot", assignment.getProfileSnapshot());
        }
        root.put("objective", task.getTitle());
        copyArray(root, "scope", details, "scope"); copyArray(root, "nonGoals", details, "nonGoals");
        copyArray(root, "acceptanceCriteria", details, "acceptanceCriteria"); copyArray(root, "verificationCommands", details, "verificationCommands");
        var blockerHistory = root.putArray("blockerHistory");
        blockers.findByTaskIdOrderByCreatedAtDesc(task.getId()).stream()
                .filter(blocker -> blocker.getStatus() != TaskBlockerStatus.OPEN)
                .forEach(blocker -> {
                    var item = blockerHistory.addObject();
                    item.put("id", blocker.getId());
                    item.put("reasonCode", blocker.getReasonCode().name());
                    item.put("summary", blocker.getSummary());
                    item.put("status", blocker.getStatus().name());
                    item.put("resolution", blocker.getResolution());
                });
        var policy = root.putObject("executionPolicy");
        policy.put("mode", "LOCAL_AGENT");
        policy.put("workingDirectory", "repository-root");
        policy.putObject("preflight")
                .put("requireRepositoryMatch", true)
                .put("requireCleanWorkingTree", false)
                .put("requireBaseCommitCheck", true);
        policy.putObject("git")
                .put("remote", "origin")
                .put("targetBranch", task.getBranchName())
                .put("createBranch", true)
                .put("commit", true)
                .put("push", true)
                .put("createPullRequest", workflow.isPullRequestRequired() ? "REQUIRED" : "OPTIONAL")
                .put("merge", false)
                .put("forcePush", false)
                .put("deleteRemoteBranch", false);
        root.putArray("allowedPaths"); root.putArray("forbiddenPaths").add(".env").add(".env.*").add("*.pem").add("*.key").add("secrets/**");
        root.putArray("deliverables").add("修改后的代码").add("测试结果").add("Commit SHA").add("Pull Request URL").add("未解决问题");
        String canonical = root.toString(); String hash = sha256(canonical);
        meta.put("packageHash", "sha256:" + hash);
        if (!packageSchema.validate(root).isEmpty()) throw new IllegalStateException("生成的任务包不符合 Schema");
        String markdown = markdown(task, workflow, project, root, packageVersion, hash);
        TaskPackage pack = packages.save(new TaskPackage(packageId, task.getId(), packageVersion, markdown, root, "sha256:" + hash,
                task.getVersion() == null ? 0L : task.getVersion(), task.getSourcePlanVersion(), task.getSourceSpecVersion(),
                assignment == null ? null : assignment.getProfileVersion(), codeContext.getBaseCommitSha(),
                codeContext.getId(), codeContext.getContextPlanId()));
        task.setCurrentPackageVersion(packageVersion);
        tasks.save(task);
        return pack;
    }

    @Transactional(readOnly = true)
    public TaskPackage current(Long actorId, Long taskId) {
        requireAccessibleTask(taskId, actorId);
        return packages.findByTaskIdAndStatus(taskId, TaskPackageStatus.CURRENT)
                .orElseThrow(() -> notFound("TASK_PACKAGE_NOT_FOUND", "当前任务包不存在"));
    }

    @Transactional(readOnly = true)
    public TaskPackage version(Long actorId, Long taskId, int version) {
        requireAccessibleTask(taskId, actorId);
        return packages.findByTaskIdAndPackageVersion(taskId, version)
                .orElseThrow(() -> notFound("TASK_PACKAGE_NOT_FOUND", "任务包不存在"));
    }

    @Transactional(readOnly = true)
    public TaskPackageDtos.PackageDiffResponse diff(Long actorId, Long taskId, int from, int to) {
        TaskPackage before = version(actorId, taskId, from);
        TaskPackage after = version(actorId, taskId, to);
        return new TaskPackageDtos.PackageDiffResponse(from, to, before.getContentHash(), after.getContentHash(),
                before.getContentMarkdown(), after.getContentMarkdown());
    }

    private Task requireAccessibleTask(Long taskId, Long actorId) {
        Task task = tasks.findById(taskId)
                .orElseThrow(() -> notFound("TASK_NOT_FOUND", "Task 不存在"));
        workflowService.get(actorId, task.getWorkflowId());
        return task;
    }

    private DocumentVersion sourcePlan(Task task) {
        return documents.findByWorkflowIdAndDocumentTypeAndVersionNo(
                        task.getWorkflowId(), DocumentType.BUILD_PLAN, task.getSourcePlanVersion())
                .orElseThrow(() -> new IllegalStateException("Approved Build Plan is missing"));
    }

    private CodeContextVersion requireCurrentContext(Workflow workflow, DocumentVersion plan) {
        if (plan.getCodeContextVersionId() == null) {
            throw conflict("CODE_CONTEXT_REQUIRED", "批准的 Build Plan 缺少 Code Context");
        }
        CodeContextVersion context = contexts.findByIdForUpdate(plan.getCodeContextVersionId())
                .orElseThrow(() -> conflict("CODE_CONTEXT_REQUIRED", "批准的 Build Plan 对应的 Code Context 不存在"));
        if (context.getInventoryVersionId() == null || context.getContextPlanId() == null) {
            throw conflict("CODE_CONTEXT_STALE", "批准的 Build Plan 对应的 Code Context 缺少取证链路");
        }
        RepoInventoryVersion inventory = inventories.findById(context.getInventoryVersionId())
                .orElseThrow(() -> conflict("CODE_CONTEXT_STALE", "Code Context 对应的 Repo Inventory 不存在"));
        CodeContextPlan contextPlan = contextPlans.findById(context.getContextPlanId())
                .orElseThrow(() -> conflict("CODE_CONTEXT_STALE", "Code Context 对应的 Context Plan 不存在"));
        if (!workflow.getProjectId().equals(context.getProjectId())
                || !workflow.getProjectId().equals(inventory.getProjectId())
                || !workflow.getProjectId().equals(contextPlan.getProjectId())
                || !workflow.getId().equals(contextPlan.getWorkflowId())
                || !context.getInventoryVersionId().equals(contextPlan.getInventoryVersionId())
                || context.getStatus() != CodeContextStatus.CURRENT
                || inventory.getStatus() != RepoInventoryStatus.CURRENT
                || contextPlan.getStatus() != CodeContextPlanStatus.USED
                || !context.getBaseCommitSha().equalsIgnoreCase(inventory.getCommitSha())) {
            throw conflict("CODE_CONTEXT_STALE", "批准的 Build Plan 对应的 Code Context 已过期");
        }
        return context;
    }

    private JsonNode taskDetails(Task task, DocumentVersion plan) {
        try {
            for (JsonNode candidate : json.readTree(plan.getContent()).path("tasks")) {
                if (task.getExternalKey().equals(candidate.path("taskKey").asText())) return candidate;
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Approved Build Plan cannot be parsed", ex);
        }
        throw new IllegalStateException("Task is missing from its approved Build Plan");
    }

    private int latestVersion(Long workflowId, DocumentType type) {
        return documents.findTopByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(workflowId, type)
                .map(DocumentVersion::getVersionNo).orElse(0);
    }

    private void copyArray(ObjectNode target, String name, JsonNode source, String field) {
        target.set(name, source.has(field) ? source.get(field).deepCopy() : json.createArrayNode());
    }

    private String markdown(Task task, Workflow workflow, Project project, JsonNode content,
                            int packageVersion, String hash) {
        StringBuilder result = new StringBuilder("# Agent 任务包\n\n## 元数据\n\n")
                .append("- 任务 ID：").append(task.getExternalKey()).append('\n')
                .append("- 工作流 ID：WF-").append(workflow.getId()).append('\n')
                .append("- 任务版本：").append(task.getVersion()).append('\n')
                .append("- 任务包版本：").append(packageVersion).append('\n')
                .append("- 设计文档版本：").append(content.path("context").path("designVersion").asInt()).append('\n')
                .append("- 规格文档版本：").append(content.path("context").path("specVersion").asInt()).append('\n')
                .append("- 构建计划版本：").append(task.getSourcePlanVersion()).append('\n')
                .append("- 代码上下文版本：").append(content.path("context").path("codeContextVersionId").asLong()).append('\n')
                .append("- 上下文计划：").append(content.path("context").path("contextPlanId").asLong()).append('\n')
                .append("- 基线分支：").append(project.getDefaultBranch()).append('\n')
                .append("- 必须使用的任务分支：").append(task.getBranchName()).append('\n')
                .append("- 基线 Commit SHA：").append(content.path("context").path("baseCommitSha").asText()).append('\n')
                .append("- 任务包哈希：sha256:").append(hash).append("\n\n")
                .append("## 任务目标\n\n").append(task.getDescription()).append("\n\n");
        appendList(result, "工作范围", content.path("scope"), false);
        appendList(result, "非目标", content.path("nonGoals"), false);
        result.append("## 相关代码上下文\n\n")
                .append("- Code Context v").append(content.path("context").path("codeContextVersionId").asLong())
                .append("；Context Plan ").append(content.path("context").path("contextPlanId").asLong()).append("。\n");
        content.path("context").path("codeEvidence").forEach(item -> result.append("- ")
                .append(item.path("path").asText()).append("：").append(item.path("reason").asText()).append('\n'));
        result.append('\n');
        if (!content.path("blockerHistory").isEmpty()) {
            result.append("## 已解决的阻塞\n\n");
            content.path("blockerHistory").forEach(item -> result.append("- [")
                    .append(item.path("status").asText()).append("] ")
                    .append(item.path("reasonCode").asText()).append("：")
                    .append(item.path("summary").asText()).append("；解决说明：")
                    .append(item.path("resolution").asText()).append('\n'));
            result.append('\n');
        }
        appendList(result, "验收标准", content.path("acceptanceCriteria"), true);
        result.append("## 验证命令\n\n```bash\n");
        content.path("verificationCommands").forEach(item -> result.append(item.asText()).append('\n'));
        result.append("```\n\n## Git 执行策略\n\n")
                .append("1. 核对仓库、origin、当前分支和 HEAD。\n")
                .append("2. 从声明的基线 Commit 创建或切换到任务分支 `").append(task.getBranchName()).append("`。\n")
                .append("3. 默认分支 `").append(project.getDefaultBranch()).append("` 仅作为基线；禁止直接在默认分支开发、提交或推送。\n")
                .append("4. 只修改任务范围内必需的文件。\n")
                .append("5. 只提交并推送任务分支 `").append(task.getBranchName()).append("`。\n")
                .append("6. ").append(workflow.isPullRequestRequired() ? "必须创建并登记 Pull Request；" : "可以按项目策略创建并登记 Pull Request；")
                .append("禁止强制推送、合并或删除远程分支。\n")
                .append("7. 禁止读取、提交或输出密钥等敏感信息。\n\n")
                .append("## 执行前检查\n\n报告仓库、分支、基线 Commit、相关文件、上下文冲突，以及当前是否可以开始工作。\n\n")
                .append("## 冲突处理规则\n\n如果任务包与仓库事实冲突、任务包已经过期，或任务需要超出范围的修改，请停止执行并报告 Blocker。\n\n")
                .append("## 最终报告\n\n")
                .append("完成任务后，请把下面的 JSON 模板补充为最终报告，并严格遵守以下契约：\n\n")
                .append("1. 最终回复只能包含一个有效 JSON 对象，不要输出解释、注释或 Markdown 代码围栏。下面的代码围栏只用于展示模板。\n")
                .append("2. 顶层必填字段必须完整保留：`schemaVersion`、`taskId`、`packageId`、`packageVersion`、`packageHash`、`outcome`、`summary`、`changedFiles`、`tests`、`git`、`acceptanceCriteria`、`unresolvedIssues`、`outOfScopeChanges`、`blockers`。\n")
                .append("3. 顶层 `summary` 是本次交付的整体摘要；`tests[].summary` 是对应验证命令的结果摘要。两者都必须填写非空字符串，不能互相替代。\n")
                .append("4. `tests` 的每一项只能包含 `command`、`status`、`summary`。禁止使用 `result`、`success`、`output`；执行结果说明写入该项的 `summary`。\n")
                .append("5. `acceptanceCriteria` 的每一项只能包含 `criterion`、`status`、`evidence`。`evidence` 用于填写验收证据；此处禁止使用 `summary`。\n")
                .append("6. `tests[].status` 只能是 `PASSED`、`FAILED`、`SKIPPED`、`NOT_RUN`；`acceptanceCriteria[].status` 只能是 `PASSED`、`FAILED`、`NOT_VERIFIED`。\n")
                .append("7. 不得修改任务包标识和 Code Context 字段，不得新增模板之外的字段。没有内容的数组必须保留为 `[]`，")
                .append(workflow.isPullRequestRequired() ? "当前 Workflow 要求填写与 Commit 匹配的 PR URL。\n" : "没有 PR 时必须使用 `null`。\n")
                .append("8. 将示例摘要、文件、验证结果和 Commit SHA 替换为真实结果。提交前自行确认 JSON 可解析、所有必填字段存在、枚举值正确且没有未知字段。\n\n")
                .append("```json\n")
                .append(finalReportTemplate(task, content, packageVersion, hash))
                .append("\n```\n");
        return result.toString();
    }

    private String finalReportTemplate(Task task, JsonNode content, int packageVersion, String hash) {
        ObjectNode report = json.createObjectNode();
        report.put("schemaVersion", "1.0");
        report.put("taskId", task.getExternalKey());
        report.put("packageId", content.path("task").path("packageId").asLong());
        report.put("packageVersion", packageVersion);
        report.put("packageHash", "sha256:" + hash);
        report.put("codeContextVersionId", content.path("context").path("codeContextVersionId").asLong());
        report.put("contextPlanId", content.path("context").path("contextPlanId").asLong());
        report.put("baseCommitSha", content.path("context").path("baseCommitSha").asText());
        report.put("outcome", "READY_FOR_REVIEW");
        report.put("summary", "请替换为本次交付的实际变更摘要");
        report.putArray("changedFiles");

        var tests = report.putArray("tests");
        content.path("verificationCommands").forEach(command -> tests.addObject()
                .put("command", command.asText())
                .put("status", "NOT_RUN")
                .put("summary", "请替换为该命令的实际执行结果"));

        report.putObject("git")
                .put("branchName", task.getBranchName())
                .put("commitSha", content.path("context").path("baseCommitSha").asText())
                .putNull("pullRequestUrl");

        var criteria = report.putArray("acceptanceCriteria");
        content.path("acceptanceCriteria").forEach(item -> criteria.addObject()
                .put("criterion", item.isTextual() ? item.asText() : item.toString())
                .put("status", "NOT_VERIFIED")
                .put("evidence", ""));
        report.putArray("unresolvedIssues");
        report.putArray("outOfScopeChanges");
        report.putArray("blockers");
        return report.toPrettyString();
    }

    private void appendList(StringBuilder target, String heading, JsonNode items, boolean checklist) {
        target.append("## ").append(heading).append("\n\n");
        items.forEach(item -> target.append(checklist ? "- [ ] " : "- ").append(item.asText()).append('\n'));
        target.append('\n');
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
