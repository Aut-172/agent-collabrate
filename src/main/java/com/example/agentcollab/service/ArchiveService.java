package com.example.agentcollab.service;

import com.example.agentcollab.domain.*;
import com.example.agentcollab.dto.ArchiveDtos;
import com.example.agentcollab.exception.ApiException;
import com.example.agentcollab.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.io.ByteArrayOutputStream;

@Service
public class ArchiveService {
    public static final String ALL = "ALL";
    public static final String WORKFLOW_DOCUMENT = "WORKFLOW_DOCUMENT";
    public static final String TASK_PACKAGE = "TASK_PACKAGE";
    public static final String DELIVERY_REPORT = "DELIVERY_REPORT";

    private final WorkflowRepository workflows;
    private final DocumentVersionRepository documents;
    private final TaskRepository tasks;
    private final TaskPackageRepository packages;
    private final TaskDeliveryRepository deliveries;
    private final DocumentDecisionRepository decisions;
    private final ProjectAccessService access;
    private final ObjectMapper json;

    public ArchiveService(WorkflowRepository workflows, DocumentVersionRepository documents, TaskRepository tasks,
                          TaskPackageRepository packages, TaskDeliveryRepository deliveries,
                          DocumentDecisionRepository decisions, ProjectAccessService access, ObjectMapper json) {
        this.workflows = workflows; this.documents = documents; this.tasks = tasks;
        this.packages = packages; this.deliveries = deliveries; this.decisions = decisions; this.access = access; this.json = json;
    }

    @Transactional(readOnly = true)
    public List<ArchiveDtos.ArchiveFile> list(Long actorId, Long projectId) {
        access.requireMember(projectId, actorId);
        return entries(projectId).stream().map(Entry::metadata).toList();
    }

    @Transactional(readOnly = true)
    public byte[] download(Long actorId, Long projectId, String category, Collection<String> requestedKeys) {
        access.requireMember(projectId, actorId);
        String normalized = normalizeCategory(category);
        Set<String> selected = requestedKeys == null ? Set.of() : new HashSet<>(requestedKeys);
        List<Entry> available = entries(projectId).stream()
                .filter(entry -> ALL.equals(normalized) || normalized.equals(entry.category()))
                .filter(entry -> selected.isEmpty() || selected.contains(entry.key()))
                .toList();
        if (available.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "ARCHIVE_EMPTY", "没有可下载的归档文件");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
                Set<String> names = new HashSet<>();
                for (Entry entry : available) {
                    String name = uniqueName(entry.fileName(), names);
                    zip.putNextEntry(new ZipEntry(name));
                    zip.write(entry.content().getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            return buffer.toByteArray();
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ARCHIVE_DOWNLOAD_FAILED", "归档文件打包失败");
        }
    }

    private List<Entry> entries(Long projectId) {
        List<Entry> result = new ArrayList<>();
        for (Workflow workflow : workflows.findByProjectIdOrderByCreatedAtAsc(projectId)) {
            String workflowDir = "workflows/WF-" + workflow.getId();
            for (DocumentVersion document : documents.findByWorkflowIdOrderByCreatedAtDesc(workflow.getId())) {
                String extension = document.getContentFormat() == DocumentFormat.JSON ? "json" : "md";
                String fileName = workflowDir + "/" + document.getDocumentType().name().toLowerCase(Locale.ROOT)
                        + "-v" + document.getVersionNo() + "." + extension;
                String content = document.getContentFormat() == DocumentFormat.MARKDOWN
                        ? appendResolvedDecisions(document) : document.getContent();
                result.add(new Entry("DOCUMENT_VERSION:" + document.getId(), WORKFLOW_DOCUMENT, fileName,
                        document.getDocumentType().name() + " v" + document.getVersionNo(), workflow.getId(), null,
                        document.getVersionNo(), extension.toUpperCase(Locale.ROOT), content, document.getCreatedAt()));
            }
            for (Task task : tasks.findByWorkflowIdOrderById(workflow.getId())) {
                String taskDir = workflowDir + "/tasks/" + safe(task.getExternalKey());
                for (TaskPackage taskPackage : packages.findByTaskIdOrderByPackageVersionDesc(task.getId())) {
                    String stem = taskDir + "/task-package-v" + taskPackage.getPackageVersion();
                    result.add(new Entry("TASK_PACKAGE:" + taskPackage.getId() + ":md", TASK_PACKAGE, stem + ".md",
                            task.getTitle() + " · Task Package v" + taskPackage.getPackageVersion(), workflow.getId(), task.getId(),
                            taskPackage.getPackageVersion(), "MARKDOWN", taskPackage.getContentMarkdown(), taskPackage.getCreatedAt()));
                    try {
                        result.add(new Entry("TASK_PACKAGE:" + taskPackage.getId() + ":json", TASK_PACKAGE, stem + ".json",
                                task.getTitle() + " · Task Package JSON v" + taskPackage.getPackageVersion(), workflow.getId(), task.getId(),
                                taskPackage.getPackageVersion(), "JSON", json.writerWithDefaultPrettyPrinter().writeValueAsString(taskPackage.getContentJson()), taskPackage.getCreatedAt()));
                    } catch (Exception ignored) { }
                }
                for (TaskDelivery delivery : deliveries.findByTaskIdOrderBySubmittedAtDesc(task.getId())) {
                    String fileName = taskDir + "/delivery-report-" + delivery.getId() + ".json";
                    try {
                        result.add(new Entry("DELIVERY_REPORT:" + delivery.getId(), DELIVERY_REPORT, fileName,
                                task.getTitle() + " · Delivery Report", workflow.getId(), task.getId(), delivery.getPackageVersion(),
                                "JSON", json.writerWithDefaultPrettyPrinter().writeValueAsString(delivery.getReportJson()), delivery.getSubmittedAt()));
                    } catch (Exception ignored) { }
                }
            }
        }
        return result;
    }

    private String appendResolvedDecisions(DocumentVersion document) {
        var resolved = decisions.findByDocumentVersionIdAndStatusOrderByDecisionKey(document.getId(), DocumentDecisionStatus.RESOLVED);
        if (resolved.isEmpty()) return document.getContent();
        StringBuilder out = new StringBuilder(document.getContent()).append("\n\n---\n\n## 已确认决策\n\n");
        for (DocumentDecision decision : resolved) {
            String label = decision.getSelectedOption();
            for (var option : decision.getOptionsJson()) {
                if (decision.getSelectedOption() != null && decision.getSelectedOption().equals(option.path("key").asText())) {
                    label = option.path("label").asText(label); break;
                }
            }
            out.append("- `").append(decision.getDecisionKey()).append("`：")
                    .append(decision.getQuestion()).append("；最终选择：")
                    .append(label).append(" (`").append(decision.getSelectedOption()).append("`)\n");
        }
        return out.toString().stripTrailing();
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) return ALL;
        String value = category.trim().toUpperCase(Locale.ROOT);
        if (!Set.of(ALL, WORKFLOW_DOCUMENT, TASK_PACKAGE, DELIVERY_REPORT).contains(value))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARCHIVE_CATEGORY", "归档分类无效");
        return value;
    }

    private static String safe(String value) {
        String normalized = value == null ? "task" : value.replaceAll("[^A-Za-z0-9._-]", "_");
        return normalized.isBlank() ? "task" : normalized.substring(0, Math.min(normalized.length(), 80));
    }

    private static String uniqueName(String name, Set<String> names) {
        if (names.add(name)) return name;
        int dot = name.lastIndexOf('.'); String stem = dot < 0 ? name : name.substring(0, dot); String ext = dot < 0 ? "" : name.substring(dot);
        int i = 2; while (!names.add(stem + "-" + i + ext)) i++; return stem + "-" + i + ext;
    }

    private record Entry(String key, String category, String fileName, String title, Long workflowId, Long taskId,
                         Integer versionNo, String contentFormat, String content, Instant createdAt) {
        ArchiveDtos.ArchiveFile metadata() { return new ArchiveDtos.ArchiveFile(key, category, fileName, title, workflowId, taskId, versionNo, contentFormat, content.getBytes(StandardCharsets.UTF_8).length, createdAt); }
    }
}
