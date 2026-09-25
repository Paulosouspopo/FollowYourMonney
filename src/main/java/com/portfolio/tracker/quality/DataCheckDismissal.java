package com.portfolio.tracker.quality;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/** Contrôle de cohérence que l'utilisateur a déclaré normal : il ne réapparaît plus. */
@Entity
@Table(name = "data_check_dismissals")
@IdClass(DataCheckDismissal.Key.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DataCheckDismissal {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "issue_key", length = 120)
    private String issueKey;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public DataCheckDismissal(UUID userId, String issueKey) {
        this.userId = userId;
        this.issueKey = issueKey;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private UUID userId;
        private String issueKey;
    }
}
