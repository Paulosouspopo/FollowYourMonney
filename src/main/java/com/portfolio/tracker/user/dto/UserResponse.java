package com.portfolio.tracker.user.dto;

import com.portfolio.tracker.user.Role;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String username,
        String preferredCurrency,
        Role role,
        /** Compte invité du mode démo. */
        boolean demo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}