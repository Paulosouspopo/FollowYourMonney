package com.portfolio.tracker.wrapped;

import com.portfolio.tracker.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Bilan de l'année : ?year=2025 (par défaut l'année écoulée, l'année en cours à partir de décembre). */
@RestController
@RequestMapping("/api/wrapped")
@RequiredArgsConstructor
public class WrappedController {

    private final WrappedService service;

    @GetMapping
    public WrappedService.Wrapped wrapped(@RequestParam(required = false) Integer year,
            @AuthenticationPrincipal CustomUserDetails user) {
        return service.wrapped(user.getId(), year);
    }
}
