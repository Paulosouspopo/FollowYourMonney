package com.portfolio.tracker.user.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String username,
        String preferredCurrency,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}