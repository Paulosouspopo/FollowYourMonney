package com.portfolio.tracker.notification;

import com.portfolio.tracker.notification.alert.AlertRuleService;
import com.portfolio.tracker.notification.dto.*;
import com.portfolio.tracker.notification.push.PushService;
import com.portfolio.tracker.notification.push.VapidKeys;
import com.portfolio.tracker.notification.report.ReportService;
import com.portfolio.tracker.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Boîte de réception, push, préférences, règles d'alerte et rapport périodique. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final AlertRuleService alertRuleService;
    private final ReportService reportService;
    private final PushService pushService;
    private final VapidKeys vapidKeys;

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

    // --------------------------------------------------------------------- push

    /** Clé publique VAPID : le navigateur en a besoin pour s'abonner. */
    @GetMapping("/push/public-key")
    public Map<String, String> pushPublicKey() {
        return Map.of("publicKey", vapidKeys.publicKey());
    }

    @PostMapping("/push/subscriptions")
    public ResponseEntity<Void> subscribe(@Valid @RequestBody PushSubscriptionRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @AuthenticationPrincipal CustomUserDetails user) {
        String label = request.deviceLabel() != null ? request.deviceLabel() : deviceLabel(userAgent);
        pushService.subscribe(user.getId(), request.endpoint(), request.keys().p256dh(), request.keys().auth(), label);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/push/unsubscribe")
    public ResponseEntity<Void> unsubscribe(@RequestBody Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails user) {
        String endpoint = body.get("endpoint");
        if (endpoint != null) {
            pushService.unsubscribe(user.getId(), endpoint);
        }
        return ResponseEntity.noContent().build();
    }

    /** Push de test, immédiat (ignore les heures calmes). */
    @PostMapping("/push/test")
    public Map<String, Integer> testPush(@AuthenticationPrincipal CustomUserDetails user) {
        int delivered = pushService.send(user.getId(), "🔔 Notifications activées",
                "Tu recevras ici tes alertes et rapports FollowYourMoney.", "/alerts", "test");
        return Map.of("delivered", delivered);
    }

    @GetMapping("/notification-preferences")
    public NotificationPreferencesDto preferences(@AuthenticationPrincipal CustomUserDetails user) {
        return NotificationPreferencesDto.of(notificationService.preferences(user.getId()),
                pushService.deviceCount(user.getId()));
    }

    @PutMapping("/notification-preferences")
    public NotificationPreferencesDto updatePreferences(@Valid @RequestBody NotificationPreferencesDto dto,
            @AuthenticationPrincipal CustomUserDetails user) {
        return NotificationPreferencesDto.of(
                notificationService.updatePreferences(user.getId(), dto.pushEnabled(), dto.quietStart(), dto.quietEnd()),
                pushService.deviceCount(user.getId()));
    }

    /** « Chrome · Windows » : pour reconnaître ses appareils. */
    static String deviceLabel(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        String browser = userAgent.contains("Edg/") ? "Edge"
                : userAgent.contains("Firefox/") ? "Firefox"
                : userAgent.contains("Chrome/") ? "Chrome"
                : userAgent.contains("Safari/") ? "Safari" : "Navigateur";
        String os = userAgent.contains("Android") ? "Android"
                : userAgent.contains("iPhone") || userAgent.contains("iPad") ? "iOS"
                : userAgent.contains("Windows") ? "Windows"
                : userAgent.contains("Mac OS") ? "macOS"
                : userAgent.contains("Linux") ? "Linux" : null;
        return os != null ? browser + " · " + os : browser;
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

    /** Sourdine : {@code until} null réactive la règle. */
    @PostMapping("/alert-rules/{id}/mute")
    public AlertRuleResponse muteRule(@PathVariable UUID id, @RequestBody Map<String, LocalDateTime> body,
            @AuthenticationPrincipal CustomUserDetails user) {
        return alertRuleService.mute(id, body.get("until"), user.getId());
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
