package com.example.agentcollab.controller;

import com.example.agentcollab.dto.ArchiveDtos;
import com.example.agentcollab.security.CurrentUser;
import com.example.agentcollab.service.ArchiveService;
import com.example.agentcollab.service.UserService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/archive-files")
public class ArchiveController {
    private final ArchiveService archive; private final UserService users;
    public ArchiveController(ArchiveService archive, UserService users) { this.archive = archive; this.users = users; }

    @GetMapping
    public List<ArchiveDtos.ArchiveFile> list(@PathVariable Long projectId) {
        return archive.list(userId(), projectId);
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> download(@PathVariable Long projectId,
                                           @RequestParam(defaultValue = "ALL") String category,
                                           @RequestParam(required = false) List<String> keys) {
        byte[] content = archive.download(userId(), projectId, category, keys);
        String safeCategory = category == null || category.isBlank() ? "all" : category.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"archive-" + safeCategory + ".zip\"")
                .body(content);
    }

    private Long userId() { return users.requireByUsername(CurrentUser.username()).getId(); }
}
