package com.example.agentcollab.service;

import com.example.agentcollab.client.CodeContextProvider;
import com.example.agentcollab.client.ProviderSyncException;
import com.example.agentcollab.domain.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CodeEvidenceCollector {
    private final CodeContextProvider provider;
    private final ObjectMapper json;
    private final int maxFilesPerRound;
    private final long maxBytesPerFile;
    private final long maxTotalEvidenceBytes;
    private final int maxExcerptChars;

    public CodeEvidenceCollector(CodeContextProvider provider, ObjectMapper json,
                                 @Value("${app.code-context.max-files-per-round:20}") int maxFilesPerRound,
                                 @Value("${app.code-context.max-file-bytes:200000}") long maxBytesPerFile,
                                 @Value("${app.code-context.max-total-evidence-bytes:1000000}") long maxTotalEvidenceBytes,
                                 @Value("${app.code-context.max-evidence-excerpt-chars:4000}") int maxExcerptChars) {
        this.provider = provider; this.json = json; this.maxFilesPerRound = maxFilesPerRound;
        this.maxBytesPerFile = maxBytesPerFile; this.maxTotalEvidenceBytes = maxTotalEvidenceBytes;
        this.maxExcerptChars = maxExcerptChars;
    }

    public EvidenceBundle collect(EvidenceContext context) {
        Map<String, RepoInventoryFile> inventory = context.inventoryFiles().stream()
                .collect(Collectors.toMap(RepoInventoryFile::getPath, Function.identity()));
        LinkedHashMap<String, String> selected = new LinkedHashMap<>();
        JsonNode targets = context.plan().getPlanJson().path("readTargets");
        targets.path("files").forEach(item -> addIfReadable(selected, inventory,
                item.path("path").asText(), item.path("reason").asText()));
        targets.path("directories").forEach(item -> inventory.keySet().stream().sorted()
                .filter(path -> isWithin(path, item.path("path").asText()))
                .forEach(path -> addIfReadable(selected, inventory, path, item.path("reason").asText())));
        targets.path("searchQueries").forEach(query -> inventory.keySet().stream().sorted()
                .filter(path -> path.toLowerCase(Locale.ROOT).contains(query.asText().toLowerCase(Locale.ROOT)))
                .forEach(path -> addIfReadable(selected, inventory, path, "Inventory path matched query: " + query.asText())));
        List<String> paths = selected.keySet().stream().limit(maxFilesPerRound).toList();
        if (paths.isEmpty()) throw new ProviderSyncException("Context Plan did not resolve any readable files", false);

        long totalBytes = 0;
        List<EvidenceFile> evidence = new ArrayList<>();
        for (CodeContextProvider.RepositoryFileContent file :
                provider.readFiles(context.project(), context.inventory().getCommitSha(), paths)) {
            if (!selected.containsKey(file.path()) || file.content() == null) continue;
            long bytes = file.content().getBytes(StandardCharsets.UTF_8).length;
            if (bytes > maxBytesPerFile || totalBytes + bytes > maxTotalEvidenceBytes) continue;
            totalBytes += bytes;
            String excerpt = file.content().length() <= maxExcerptChars
                    ? file.content() : file.content().substring(0, maxExcerptChars);
            RepoInventoryFile indexed = inventory.get(file.path());
            evidence.add(new EvidenceFile(file.path(), file.contentHash(), evidenceType(indexed.getFileType()),
                    selected.get(file.path()), excerpt, bytes));
        }
        if (evidence.isEmpty()) throw new ProviderSyncException("Code Context evidence budget produced no files", false);

        ObjectNode bundle = json.createObjectNode();
        bundle.put("provider", "GIT"); bundle.put("inventoryVersionId", context.inventory().getId());
        bundle.put("contextPlanId", context.plan().getId()); bundle.put("baseCommitSha", context.inventory().getCommitSha());
        bundle.put("roundsUsed", 1); bundle.put("evidenceBytes", totalBytes);
        var related = bundle.putArray("relatedFiles");
        evidence.forEach(file -> related.addObject().put("path", file.path()).put("reason", file.reason())
                .put("evidenceType", file.evidenceType().name()).put("sizeBytes", file.sizeBytes()));
        bundle.set("uncertainties", context.plan().getPlanJson().path("uncertainties").deepCopy());
        return new EvidenceBundle(bundle, List.copyOf(evidence));
    }

    private void addIfReadable(Map<String, String> selected, Map<String, RepoInventoryFile> inventory,
                               String path, String reason) {
        RepoInventoryFile file = inventory.get(path);
        if (file != null && file.getFileType() != RepoFileType.BINARY && file.getSizeBytes() <= maxBytesPerFile) {
            selected.putIfAbsent(path, reason);
        }
    }

    private boolean isWithin(String path, String directory) {
        String prefix = directory.endsWith("/") ? directory : directory + "/";
        return path.startsWith(prefix);
    }

    private CodeEvidenceType evidenceType(RepoFileType type) {
        return switch (type) {
            case CONFIG -> CodeEvidenceType.CONFIG;
            case MIGRATION -> CodeEvidenceType.MIGRATION;
            case TEST -> CodeEvidenceType.TEST;
            case DOC -> CodeEvidenceType.DOC;
            default -> CodeEvidenceType.SOURCE_FILE;
        };
    }

    public record EvidenceContext(Project project, CodeContextPlan plan, RepoInventoryVersion inventory,
                                  List<RepoInventoryFile> inventoryFiles) {}
    public record EvidenceBundle(JsonNode evidenceJson, List<EvidenceFile> files) {}
    public record EvidenceFile(String path, String contentHash, CodeEvidenceType evidenceType,
                               String reason, String excerpt, long sizeBytes) {}
}
