package com.portfolio.tracker.tutorial;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Tutoriels d'un compte : visites terminées (ou ignorées) et affichage automatique. */
@Entity
@Table(name = "tutorial_states")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TutorialState {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "auto_enabled", nullable = false)
    private boolean autoEnabled = true;

    /** Clés séparées par des virgules. */
    @Column(nullable = false, length = 2000)
    private String completed = "";

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public TutorialState(UUID userId) {
        this.userId = userId;
    }

    public Set<String> completedKeys() {
        Set<String> keys = new LinkedHashSet<>();
        Arrays.stream(completed.split(",")).map(String::trim).filter(k -> !k.isEmpty()).forEach(keys::add);
        return keys;
    }

    public void setCompletedKeys(Set<String> keys) {
        this.completed = String.join(",", keys);
    }
}
