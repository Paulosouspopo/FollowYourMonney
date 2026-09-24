package com.portfolio.tracker.imports;

import com.portfolio.tracker.imports.dto.ImportCommitRequest;
import com.portfolio.tracker.imports.dto.ImportCommitResult;
import com.portfolio.tracker.imports.dto.ImportInspection;
import com.portfolio.tracker.imports.dto.ImportPreview;
import com.portfolio.tracker.imports.dto.PreviewOptions;
import com.portfolio.tracker.security.CustomUserDetails;
import com.portfolio.tracker.shared.exception.BadRequestException;
import com.portfolio.tracker.shared.ratelimit.RateLimiter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;

/**
 * Import de relevés : inspection (format, extrait) → aperçu → validation.
 * Le fichier est envoyé à chaque étape de lecture et n'est jamais stocké.
 */
@RestController
@RequestMapping("/api/imports")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;
    private final RateLimiter rateLimiter;

    @PostMapping(path = "/inspect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportInspection> inspect(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(importService.inspect(bytes(file)));
    }

    /** Aperçu : appelle Yahoo pour chaque actif du relevé, d'où une limite par utilisateur. */
    @PostMapping(path = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportPreview> preview(@RequestPart("file") MultipartFile file,
            @Valid @RequestPart("options") PreviewOptions options,
            @AuthenticationPrincipal CustomUserDetails user) {
        rateLimiter.check("import-preview:" + user.getId(), 30, Duration.ofHours(1));
        return ResponseEntity.ok(importService.preview(user.getId(), options, bytes(file)));
    }

    @PostMapping("/commit")
    public ResponseEntity<ImportCommitResult> commit(@Valid @RequestBody ImportCommitRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(importService.commit(user.getId(), request));
    }

    private static byte[] bytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Aucun fichier reçu");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Fichier illisible");
        }
    }
}
