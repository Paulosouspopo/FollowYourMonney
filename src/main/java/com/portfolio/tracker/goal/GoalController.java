package com.portfolio.tracker.goal;

import com.portfolio.tracker.goal.dto.GoalRequest;
import com.portfolio.tracker.goal.dto.GoalResponse;
import com.portfolio.tracker.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Objectifs d'épargne. */
@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    @GetMapping
    public List<GoalResponse> list(@AuthenticationPrincipal CustomUserDetails user) {
        return goalService.findAll(user.getId());
    }

    @PostMapping
    public ResponseEntity<GoalResponse> create(@Valid @RequestBody GoalRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(goalService.create(request, user.getId()));
    }

    @PutMapping("/{id}")
    public GoalResponse update(@PathVariable UUID id, @Valid @RequestBody GoalRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return goalService.update(id, request, user.getId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails user) {
        goalService.delete(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
