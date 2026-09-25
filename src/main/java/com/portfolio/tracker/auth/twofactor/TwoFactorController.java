package com.portfolio.tracker.auth.twofactor;

import com.portfolio.tracker.security.CustomUserDetails;
import com.portfolio.tracker.shared.ratelimit.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Réglage de la double authentification (compte connecté). */
@RestController
@RequestMapping("/api/account/2fa")
@RequiredArgsConstructor
public class TwoFactorController {

    private static final Duration QUARTER_HOUR = Duration.ofMinutes(15);

    private final TwoFactorService service;
    private final RateLimiter rateLimiter;

    @GetMapping
    public TwoFactorService.Status status(@AuthenticationPrincipal CustomUserDetails user) {
        return service.status(user.getId());
    }

    @PostMapping("/setup")
    public TwoFactorService.Setup setup(@AuthenticationPrincipal CustomUserDetails user) {
        return service.setup(user.getId());
    }

    /** @return { "recoveryCodes": [...] } : affichés une seule fois */
    @PostMapping("/enable")
    public Map<String, List<String>> enable(@RequestBody Map<String, String> body, @AuthenticationPrincipal CustomUserDetails user) {
        rateLimiter.check("2fa-enable:" + user.getId(), 10, QUARTER_HOUR);
        return Map.of("recoveryCodes", service.enable(user.getId(), body.get("code")));
    }

    @PostMapping("/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@RequestBody Map<String, String> body, @AuthenticationPrincipal CustomUserDetails user) {
        rateLimiter.check("2fa-disable:" + user.getId(), 10, QUARTER_HOUR);
        service.disable(user.getId(), body.get("password"), body.get("code"));
    }

    @PostMapping("/recovery-codes")
    public Map<String, List<String>> recoveryCodes(@RequestBody Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails user) {
        rateLimiter.check("2fa-codes:" + user.getId(), 10, QUARTER_HOUR);
        return Map.of("recoveryCodes", service.regenerateRecoveryCodes(user.getId(), body.get("code")));
    }
}
