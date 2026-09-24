package com.portfolio.tracker.notification;

import com.portfolio.tracker.notification.alert.AlertRuleService;
import com.portfolio.tracker.notification.dto.*;
import com.portfolio.tracker.notification.report.ReportService;
import com.portfolio.tracker.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Boîte de réception, règles d'alerte et rapport périodique. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final AlertRuleService alertRuleService;
    private final ReportService reportService;

    // ------------------------------------------------------------- boîte de réception

    @GetMapping("/notifications")
    public List<NotificationResponse> notifications(@RequestParam(defaultValue = "50") int limit,
            @AuthenticationPrincipal CustomUserDetails user) {
        return notificationService.recent(user.getId(), limit).stream().map(NotificationResponse::of).toList();
    }

    @GetMapping("/notifications/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal CustomUserDetails user) {
        return Map.of("count", notificationService.unreadCount(user.getId()));
    }

    @PostMapping("/notifications/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails user) {
        notificationService.markRead(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/notifications/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal CustomUserDetails user) {
        notificationService.markAllRead(user.getId());
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ règles

    @GetMapping("/alert-rules")
    public List<AlertRuleResponse> rules(@AuthenticationPrincipal CustomUserDetails user) {
        return alertRuleService.findAll(user.getId());
    }

    @PostMapping("/alert-rules")
    public ResponseEntity<AlertRuleResponse> createRule(@Valid @RequestBody AlertRuleRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(alertRuleService.create(request, user.getId()));
    }

    @PutMapping("/alert-rules/{id}")
    public AlertRuleResponse updateRule(@PathVariable UUID id, @Valid @RequestBody AlertRuleRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return alertRuleService.update(id, request, user.getId());
    }

    @DeleteMapping("/alert-rules/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails user) {
        alertRuleService.delete(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ rapport

    @GetMapping("/report-settings")
    public ReportSettingsDto reportSettings(@AuthenticationPrincipal CustomUserDetails user) {
        return reportService.settings(user.getId());
    }

    @PutMapping("/report-settings")
    public ReportSettingsDto updateReportSettings(@Valid @RequestBody ReportSettingsDto dto,
            @AuthenticationPrincipal CustomUserDetails user) {
        return reportService.updateSettings(user.getId(), dto);
    }

    /** Le rapport tel qu'il serait envoyé maintenant (rien n'est envoyé). */
    @GetMapping("/report-settings/preview")
    public ReportPreview reportPreview(@AuthenticationPrincipal CustomUserDetails user) {
        return reportService.preview(user.getId(), reportService.settings(user.getId()).frequency());
    }
}
