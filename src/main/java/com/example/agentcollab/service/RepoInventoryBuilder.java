package com.example.agentcollab.service;

import com.example.agentcollab.client.CodeContextProvider;
import com.example.agentcollab.domain.Project;
import com.example.agentcollab.domain.RepoFileType;
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
public class RepoInventoryBuilder {
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "webp", "ico", "pdf", "zip", "gz", "jar", "class", "exe", "dll");
    private static final Set<String> SENSITIVE_NAMES = Set.of(
            ".env", "id_rsa", "id_ed25519", "credentials", "credentials.json", "secrets.json");

    private final CodeContextProvider provider;
    private final ObjectMapper json;
    private final long maxFileBytes;
    private final int maxBootstrapFiles;
    private final int maxSummaryChars;

    public RepoInventoryBuilder(CodeContextProvider provider, ObjectMapper json,
                                @Value("${app.code-context.max-file-bytes:200000}") long maxFileBytes,
                                @Value("${app.code-context.max-bootstrap-files:20}") int maxBootstrapFiles,
                                @Value("${app.code-context.max-indexed-summary-chars:2000}") int maxSummaryChars) {
        this.provider = provider;
        this.json = json;
        this.maxFileBytes = maxFileBytes;
        this.maxBootstrapFiles = maxBootstrapFiles;
        this.maxSummaryChars = maxSummaryChars;
    }

    public InventorySnapshot fetch(Project project) {
        CodeContextProvider.RepositoryHead head = provider.readHead(project);
        if (!project.getDefaultBranch().equals(head.branchName())
                || head.commitSha() == null || !head.commitSha().matches("[a-fA-F0-9]{7,64}")) {
            throw new com.example.agentcollab.client.ProviderSyncException(
                    "Git Provider returned an invalid repository head", false);
        }
        Map<String, CodeContextProvider.RepositoryFileFact> uniqueFiles =
                provider.readTree(project, head.commitSha()).stream()
                .filter(file -> isSafeRelativePath(file.path()))
                .filter(file -> !isSensitive(file.path()))
                .sorted(Comparator.comparing(CodeContextProvider.RepositoryFileFact::path))
                .collect(Collectors.toMap(CodeContextProvider.RepositoryFileFact::path, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
        List<CodeContextProvider.RepositoryFileFact> safeFiles = List.copyOf(uniqueFiles.values());
        List<String> bootstrapPaths = safeFiles.stream()
                .filter(file -> file.sizeBytes() <= maxFileBytes)
                .filter(file -> classify(file.path()) != RepoFileType.BINARY)
                .filter(file -> isBootstrapPath(file.path()))
                .sorted(Comparator.comparingInt((CodeContextProvider.RepositoryFileFact file) ->
                                bootstrapPriority(file.path()))
                        .thenComparing(CodeContextProvider.RepositoryFileFact::path))
                .limit(maxBootstrapFiles)
                .map(CodeContextProvider.RepositoryFileFact::path)
                .toList();
        Map<String, CodeContextProvider.RepositoryFileContent> contentByPath =
                provider.readFiles(project, head.commitSha(), bootstrapPaths).stream()
                        .collect(Collectors.toMap(CodeContextProvider.RepositoryFileContent::path,
                                Function.identity(), (left, right) -> left));

        List<InventoryFile> files = safeFiles.stream().map(file -> {
            var content = contentByPath.get(file.path());
            String hash = content != null && content.contentHash() != null
                    ? content.contentHash() : file.contentHash();
            String summary = content == null || content.content() == null
                    || content.content().getBytes(StandardCharsets.UTF_8).length > maxFileBytes
                    ? null : summarize(content.content());
            return new InventoryFile(file.path(), classify(file.path()), Math.max(0, file.sizeBytes()), hash, summary);
        }).toList();
        return new InventorySnapshot(head.branchName(), head.commitSha(), repositoryProfile(files),
                treeSummary(files, bootstrapPaths.size()), files);
    }

    private JsonNode repositoryProfile(List<InventoryFile> files) {
        ObjectNode profile = json.createObjectNode();
        var stack = profile.putArray("techStack");
        if (hasSuffix(files, ".java")) stack.add("Java");
        if (hasSuffix(files, ".kt")) stack.add("Kotlin");
        if (hasSuffix(files, ".js") || hasSuffix(files, ".ts")) stack.add("JavaScript/TypeScript");
        var buildTools = profile.putArray("buildTools");
        if (hasPath(files, "pom.xml")) buildTools.add("Maven");
        if (hasPath(files, "build.gradle") || hasPath(files, "build.gradle.kts")) buildTools.add("Gradle");
        if (hasPath(files, "package.json")) buildTools.add("npm-compatible");
        var testFrameworks = profile.putArray("testLocations");
        files.stream().map(InventoryFile::path).filter(path -> path.contains("/test/") || path.startsWith("test/"))
                .map(RepoInventoryBuilder::topDirectory).distinct().limit(20).forEach(testFrameworks::add);
        profile.put("fileCount", files.size());
        return profile;
    }

    private JsonNode treeSummary(List<InventoryFile> files, int indexedFileCount) {
        ObjectNode summary = json.createObjectNode();
        summary.put("fileCount", files.size());
        summary.put("indexedFileCount", indexedFileCount);
        summary.put("maxFileBytes", maxFileBytes);
        ObjectNode counts = summary.putObject("fileTypeCounts");
        Arrays.stream(RepoFileType.values()).forEach(type -> counts.put(type.name(),
                files.stream().filter(file -> file.fileType() == type).count()));
        var roots = summary.putArray("topLevelPaths");
        files.stream().map(InventoryFile::path).map(RepoInventoryBuilder::topDirectory)
                .distinct().sorted().limit(100).forEach(roots::add);
        return summary;
    }

    private String summarize(String content) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (normalized.length() > maxSummaryChars) normalized = normalized.substring(0, maxSummaryChars);
        int bytes = content.getBytes(StandardCharsets.UTF_8).length;
        return "Indexed text excerpt (" + bytes + " bytes):\n" + normalized;
    }

    private boolean isBootstrapPath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.matches("(^|.*/)readme[^/]*")
                || lower.endsWith("pom.xml") || lower.endsWith("build.gradle")
                || lower.endsWith("build.gradle.kts") || lower.endsWith("package.json")
                || lower.startsWith("src/main/") || lower.startsWith("src/test/")
                || lower.startsWith(".github/workflows/") || lower.startsWith("docs/");
    }

    private int bootstrapPriority(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (!lower.contains("/") || lower.startsWith(".github/workflows/")) return 0;
        if (lower.contains("/db/migration/")) return 1;
        if (lower.startsWith("src/main/") || lower.startsWith("src/test/")) return 2;
        return 3;
    }

    private boolean isSensitive(String path) {
        String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        return SENSITIVE_NAMES.contains(name) || name.startsWith(".env.")
                || name.endsWith(".pem") || name.endsWith(".key") || name.endsWith(".p12")
                || name.endsWith(".pfx") || normalized.contains("/secrets/")
                || normalized.startsWith("secrets/") || normalized.contains("/.ssh/");
    }

    private boolean isSafeRelativePath(String path) {
        if (path == null || path.isBlank()) return false;
        String normalized = path.replace('\\', '/');
        return !normalized.startsWith("/") && !normalized.contains("../") && !normalized.equals("..");
    }

    private RepoFileType classify(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        String extension = lower.contains(".") ? lower.substring(lower.lastIndexOf('.') + 1) : "";
        if (BINARY_EXTENSIONS.contains(extension)) return RepoFileType.BINARY;
        if (lower.contains("/migration/") || lower.contains("/db/migration/")) return RepoFileType.MIGRATION;
        if (lower.startsWith("src/test/") || lower.contains("/test/")) return RepoFileType.TEST;
        if (lower.startsWith("docs/") || lower.endsWith(".md")) return RepoFileType.DOC;
        if (lower.endsWith("pom.xml") || lower.endsWith("build.gradle") || lower.endsWith("build.gradle.kts")
                || lower.endsWith("package.json") || lower.startsWith(".github/workflows/")
                || lower.endsWith(".yml") || lower.endsWith(".yaml") || lower.endsWith(".properties")) {
            return RepoFileType.CONFIG;
        }
        if (Set.of("java", "kt", "js", "ts", "tsx", "jsx", "go", "rs", "py", "cs", "sql").contains(extension)) {
            return RepoFileType.SOURCE;
        }
        return RepoFileType.UNKNOWN;
    }

    private boolean hasSuffix(List<InventoryFile> files, String suffix) {
        return files.stream().anyMatch(file -> file.path().toLowerCase(Locale.ROOT).endsWith(suffix));
    }

    private boolean hasPath(List<InventoryFile> files, String name) {
        return files.stream().anyMatch(file -> file.path().equalsIgnoreCase(name));
    }

    private static String topDirectory(String path) {
        int separator = path.indexOf('/');
        return separator < 0 ? path : path.substring(0, separator);
    }

    public record InventorySnapshot(String branchName, String commitSha, JsonNode repositoryProfile,
                                    JsonNode treeSummary, List<InventoryFile> files) {}
    public record InventoryFile(String path, RepoFileType fileType, long sizeBytes,
                                String contentHash, String indexedSummary) {}
}
