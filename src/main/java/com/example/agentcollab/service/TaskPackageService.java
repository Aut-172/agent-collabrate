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
    private final WorkflowService workflowService;
    private final ObjectMapper json;
    private final JsonSchema packageSchema;
    private final EntityManager entityManager;

    public TaskPackageService(TaskPackageRepository packages, TaskRepository tasks, WorkflowRepository workflows,
                              ProjectRepository projects, ProjectMemberRepository members,
                              TaskAssignmentRepository assignments, DocumentVersionRepository documents,
                              WorkflowService workflowService, ObjectMapper json, EntityManager entityManager) {
        this.packages = packages; this.tasks = tasks; this.workflows = workflows; this.projects = projects;
        this.members = members;
        this.assignments = assignments; this.documents = documents; this.workflowService = workflowService; this.json = json;
        this.entityManager = entityManager;
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
        return replacement;
    }

    private TaskPackage create(Task task, int packageVersion) {
        Long packageId = ((Number) entityManager.createNativeQuery(
                "SELECT nextval('task_packages_id_seq')").getSingleResult()).longValue();
        Workflow workflow = workflows.findById(task.getWorkflowId()).orElseThrow();
        Project project = projects.findById(workflow.getProjectId()).orElseThrow();
        TaskAssignment assignment = assignments.findByTaskIdAndCurrentTrue(task.getId()).orElse(null);
        JsonNode details = taskDetails(task);
        var root = json.createObjectNode();
        root.put("schemaVersion", "1.0");
        var meta = root.putObject("task");
        meta.put("taskId", task.getExternalKey()); meta.put("workflowId", "WF-" + workflow.getId());
        meta.put("packageId", packageId);
        meta.put("taskVersion", task.getVersion() == null ? 0 : task.getVersion());
        meta.put("packageVersion", packageVersion);
        meta.put("status", "CURRENT");
        meta.put("generatedAt", java.time.Instant.now().toString());
        root.putObject("project").put("projectId", project.getId()).put("name", project.getName())
                .put("repositoryUrl", project.getRepositoryUrl()).put("defaultBranch", project.getDefaultBranch());
        var context = root.putObject("context");
        context.put("designVersion", latestVersion(task.getWorkflowId(), DocumentType.DESIGN));
        context.put("specVersion", task.getSourceSpecVersion() == null ? 0 : task.getSourceSpecVersion());
        context.put("buildPlanVersion", task.getSourcePlanVersion()); context.put("baseBranch", project.getDefaultBranch());
        context.put("baseCommit", "UNKNOWN"); context.putArray("relevantPaths");
        if (assignment != null) {
            var a = root.putObject("assignee"); a.put("userId", assignment.getAssigneeUserId());
            members.findByProjectIdAndUserId(workflow.getProjectId(), assignment.getAssigneeUserId())
                    .ifPresent(member -> a.put("projectRole", member.getProjectRole().name()));
            a.put("profileVersion", assignment.getProfileVersion()); a.set("profileSnapshot", assignment.getProfileSnapshot());
        }
        root.put("objective", task.getTitle());
        copyArray(root, "scope", details, "scope"); copyArray(root, "nonGoals", details, "nonGoals");
        copyArray(root, "acceptanceCriteria", details, "acceptanceCriteria"); copyArray(root, "verificationCommands", details, "verificationCommands");
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
                .put("createPullRequest", "OPTIONAL")
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
                assignment == null ? null : assignment.getProfileVersion(), "UNKNOWN"));
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

    private JsonNode taskDetails(Task task) {
        DocumentVersion plan = documents.findByWorkflowIdAndDocumentTypeAndVersionNo(
                        task.getWorkflowId(), DocumentType.BUILD_PLAN, task.getSourcePlanVersion())
                .orElseThrow(() -> new IllegalStateException("Approved Build Plan is missing"));
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
        StringBuilder result = new StringBuilder("# Agent Task Package\n\n## Metadata\n\n")
                .append("- Task ID: ").append(task.getExternalKey()).append('\n')
                .append("- Workflow ID: WF-").append(workflow.getId()).append('\n')
                .append("- Task version: ").append(task.getVersion()).append('\n')
                .append("- Package version: ").append(packageVersion).append('\n')
                .append("- Design version: ").append(content.path("context").path("designVersion").asInt()).append('\n')
                .append("- Spec version: ").append(content.path("context").path("specVersion").asInt()).append('\n')
                .append("- Build plan version: ").append(task.getSourcePlanVersion()).append('\n')
                .append("- Base branch: ").append(project.getDefaultBranch()).append('\n')
                .append("- Base commit: UNKNOWN\n")
                .append("- Package hash: sha256:").append(hash).append("\n\n")
                .append("## Objective\n\n").append(task.getDescription()).append("\n\n");
        appendList(result, "Scope", content.path("scope"), false);
        appendList(result, "Non-goals", content.path("nonGoals"), false);
        appendList(result, "Acceptance Criteria", content.path("acceptanceCriteria"), true);
        result.append("## Verification\n\n```bash\n");
        content.path("verificationCommands").forEach(item -> result.append(item.asText()).append('\n'));
        result.append("```\n\n## Git Execution Policy\n\n")
                .append("1. Verify the repository, origin, current branch, and HEAD.\n")
                .append("2. Create or switch to the task branch from the declared base commit.\n")
                .append("3. Modify only files required by the task scope.\n")
                .append("4. Commit and push only the task branch.\n")
                .append("5. Do not push the default branch, force push, merge, or delete remote branches.\n")
                .append("6. Do not read, commit, or output secrets.\n\n")
                .append("## Preflight\n\nReport repository, branch, base commit, relevant files, context conflicts, and whether work can start.\n\n")
                .append("## Conflict Rule\n\nStop and report a blocker if the package conflicts with the repository, is stale, or requires out-of-scope changes.\n\n")
                .append("## Final Report\n\nReport changed files, verification results, branch, commit SHA, PR URL, unresolved issues, and out-of-scope changes.\n");
        return result.toString();
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
}
