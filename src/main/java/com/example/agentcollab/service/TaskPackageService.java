package com.example.agentcollab.service;

import com.example.agentcollab.domain.*;
import com.example.agentcollab.dto.TaskPackageDtos;
import com.example.agentcollab.exception.ApiException;
import com.example.agentcollab.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class TaskPackageService {
    private final TaskPackageRepository packages;
    private final TaskRepository tasks;
    private final WorkflowRepository workflows;
    private final ProjectRepository projects;
    private final TaskAssignmentRepository assignments;
    private final DocumentVersionRepository documents;
    private final WorkflowService workflowService;
    private final ObjectMapper json;
    private final JsonSchema packageSchema;

    public TaskPackageService(TaskPackageRepository packages, TaskRepository tasks, WorkflowRepository workflows,
                              ProjectRepository projects, TaskAssignmentRepository assignments,
                              DocumentVersionRepository documents, WorkflowService workflowService, ObjectMapper json) {
        this.packages = packages; this.tasks = tasks; this.workflows = workflows; this.projects = projects;
        this.assignments = assignments; this.documents = documents; this.workflowService = workflowService; this.json = json;
        this.packageSchema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(getClass().getResourceAsStream("/schema/task-package-v1.schema.json"));
    }

    @Transactional
    public TaskPackage createInitial(Task task) {
        if (packages.findTopByTaskIdOrderByPackageVersionDesc(task.getId()).isPresent())
            return packages.findTopByTaskIdOrderByPackageVersionDesc(task.getId()).orElseThrow();
        Workflow workflow = workflows.findById(task.getWorkflowId()).orElseThrow();
        Project project = projects.findById(workflow.getProjectId()).orElseThrow();
        TaskAssignment assignment = assignments.findByTaskIdAndCurrentTrue(task.getId()).orElse(null);
        JsonNode details = taskDetails(task);
        var root = json.createObjectNode();
        root.put("schemaVersion", "1.0");
        var meta = root.putObject("task");
        meta.put("taskId", "TASK-" + task.getId()); meta.put("workflowId", "WF-" + workflow.getId());
        meta.put("taskVersion", task.getVersion() == null ? 0 : task.getVersion());
        meta.put("packageVersion", 1); meta.put("status", "CURRENT"); meta.put("generatedAt", java.time.Instant.now().toString());
        root.putObject("project").put("projectId", project.getId()).put("name", project.getName())
                .put("repositoryUrl", project.getRepositoryUrl()).put("defaultBranch", project.getDefaultBranch());
        var context = root.putObject("context");
        context.put("designVersion", latestVersion(task.getWorkflowId(), DocumentType.DESIGN));
        context.put("specVersion", task.getSourceSpecVersion() == null ? 0 : task.getSourceSpecVersion());
        context.put("buildPlanVersion", task.getSourcePlanVersion()); context.put("baseBranch", project.getDefaultBranch());
        context.put("baseCommit", "UNKNOWN"); context.putArray("relevantPaths");
        if (assignment != null) {
            var a = root.putObject("assignee"); a.put("userId", assignment.getAssigneeUserId());
            a.put("profileVersion", assignment.getProfileVersion()); a.set("profileSnapshot", assignment.getProfileSnapshot());
        }
        root.put("objective", task.getTitle());
        copyArray(root, "scope", details, "scope"); copyArray(root, "nonGoals", details, "nonGoals");
        copyArray(root, "acceptanceCriteria", details, "acceptanceCriteria"); copyArray(root, "verificationCommands", details, "verificationCommands");
        var policy = root.putObject("executionPolicy"); policy.put("mode", "LOCAL_AGENT");
        policy.putObject("git").put("remote", "origin").put("targetBranch", task.getBranchName())
                .put("createBranch", true).put("commit", true).put("push", true).put("merge", false).put("forcePush", false);
        root.putArray("allowedPaths"); root.putArray("forbiddenPaths").add(".env").add(".env.*").add("*.pem").add("*.key").add("secrets/**");
        root.putArray("deliverables").add("修改后的代码").add("测试结果").add("Commit SHA").add("Pull Request URL").add("未解决问题");
        String canonical = root.toString(); String hash = sha256(canonical);
        if (!packageSchema.validate(root).isEmpty()) throw new IllegalStateException("生成的任务包不符合 Schema");
        String markdown = markdown(task, workflow, project, root, details, hash);
        TaskPackage pack = packages.save(new TaskPackage(task.getId(), 1, markdown, root, "sha256:" + hash,
                task.getVersion() == null ? 0L : task.getVersion(), task.getSourcePlanVersion(), task.getSourceSpecVersion(),
                assignment == null ? null : assignment.getProfileVersion(), "UNKNOWN"));
        task.setCurrentPackageVersion(1); tasks.save(task); return pack;
    }

    @Transactional(readOnly = true) public TaskPackage current(Long actorId, Long taskId) { Task t = task(taskId, actorId); return packages.findByTaskIdAndStatus(taskId, TaskPackageStatus.CURRENT).orElseThrow(() -> notFound("TASK_PACKAGE_NOT_FOUND", "当前任务包不存在")); }
    @Transactional(readOnly = true) public TaskPackage version(Long actorId, Long taskId, int version) { task(taskId, actorId); return packages.findByTaskIdAndPackageVersion(taskId, version).orElseThrow(() -> notFound("TASK_PACKAGE_NOT_FOUND", "任务包不存在")); }
    @Transactional(readOnly = true) public TaskPackageDtos.PackageDiffResponse diff(Long actorId, Long taskId, int from, int to) { var a=version(actorId,taskId,from); var b=version(actorId,taskId,to); return new TaskPackageDtos.PackageDiffResponse(from,to,a.getContentHash(),b.getContentHash(),a.getContentMarkdown(),b.getContentMarkdown()); }
    private Task task(Long taskId, Long actorId) { Task t=tasks.findById(taskId).orElseThrow(() -> notFound("TASK_NOT_FOUND", "Task 不存在")); workflowService.get(actorId,t.getWorkflowId()); return t; }
    private JsonNode taskDetails(Task t) { return documents.findByWorkflowIdAndDocumentTypeAndVersionNo(t.getWorkflowId(), DocumentType.BUILD_PLAN, t.getSourcePlanVersion()).map(d -> { try { for(JsonNode n: json.readTree(d.getContent()).path("tasks")) if(t.getExternalKey().equals(n.path("taskKey").asText())) return n; } catch(Exception ignored){} return json.createObjectNode(); }).orElse(json.createObjectNode()); }
    private int latestVersion(Long id, DocumentType type) { return documents.findTopByWorkflowIdAndDocumentTypeOrderByVersionNoDesc(id,type).map(DocumentVersion::getVersionNo).orElse(0); }
    private void copyArray(com.fasterxml.jackson.databind.node.ObjectNode root,String name,JsonNode src,String field){ root.set(name, src != null && src.has(field) ? src.get(field).deepCopy() : json.createArrayNode()); }
    private String markdown(Task t, Workflow w, Project p, JsonNode root, JsonNode details, String hash) { StringBuilder b=new StringBuilder("# Agent Task Package\n\n## Metadata\n\n- Task ID: ").append(t.getId()).append("\n- Workflow ID: ").append(w.getId()).append("\n- Package version: 1\n- Base branch: ").append(p.getDefaultBranch()).append("\n- Base commit: UNKNOWN\n- Package hash: sha256:").append(hash).append("\n\n## Objective\n\n").append(t.getTitle()).append("\n\n## Scope\n"); for(JsonNode n: root.path("scope")) b.append("- ").append(n.asText()).append('\n'); b.append("\n## Acceptance Criteria\n"); for(JsonNode n: root.path("acceptanceCriteria")) b.append("- [ ] ").append(n.asText()).append('\n'); b.append("\n## Verification\n\n```bash\n"); for(JsonNode n: root.path("verificationCommands")) b.append(n.asText()).append('\n'); return b.append("```\n").toString(); }
    private String sha256(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch(Exception e){ throw new IllegalStateException(e); } }
    private ApiException notFound(String c,String m){return new ApiException(HttpStatus.NOT_FOUND,c,m);}
}
