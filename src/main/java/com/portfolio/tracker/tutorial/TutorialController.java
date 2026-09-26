package com.portfolio.tracker.tutorial;

import com.portfolio.tracker.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Tutoriels : état par compte (visites terminées, affichage automatique). */
@RestController
@RequestMapping("/api/tutorials")
@RequiredArgsConstructor
public class TutorialController {

    private final TutorialService service;

    @GetMapping
    public TutorialService.State get(@AuthenticationPrincipal CustomUserDetails user) {
        return service.get(user.getId());
    }

    /** Visite terminée ou ignorée : elle ne se relance plus d'elle-même. */
    @PostMapping("/{key}/complete")
    public TutorialService.State complete(@PathVariable String key, @AuthenticationPrincipal CustomUserDetails user) {
        return service.complete(user.getId(), key);
    }

    @PutMapping("/settings")
    public TutorialService.State settings(@RequestBody Map<String, Boolean> body, @AuthenticationPrincipal CustomUserDetails user) {
        return service.setAutoEnabled(user.getId(), !Boolean.FALSE.equals(body.get("autoEnabled")));
    }

    @DeleteMapping
    public TutorialService.State reset(@AuthenticationPrincipal CustomUserDetails user) {
        return service.reset(user.getId());
    }
}
