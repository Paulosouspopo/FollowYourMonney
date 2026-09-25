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
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}