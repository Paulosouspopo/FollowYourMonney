package com.portfolio.tracker.notification.dto;

import com.portfolio.tracker.notification.Notification;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        Notification.Type type,
        String title,
        String body,
        String link,
        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationResponse of(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getLink(),
                n.getReadAt() != null, n.getCreatedAt());
    }
}
