package com.portfolio.tracker.user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Le rôle est stocké en base. Pour éviter une requête SQL manuelle, les
 * emails listés dans {@code app.admin.emails} sont promus ADMIN au démarrage
 * (utile pour le premier administrateur d'une nouvelle installation).
 */
@Component
@Slf4j
public class AdminBootstrap {

    private final UserRepository userRepository;
    private final List<String> adminEmails;

    public AdminBootstrap(UserRepository userRepository,
            @Value("${app.admin.emails:}") List<String> adminEmails) {
        this.userRepository = userRepository;
        this.adminEmails = adminEmails;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void promoteConfiguredAdmins() {
        adminEmails.stream()
                .map(String::trim)
                .filter(email -> !email.isEmpty())
                .forEach(email -> userRepository.findByEmail(email)
                        .filter(user -> user.getRole() != Role.ADMIN)
                        .ifPresent(user -> {
                            user.setRole(Role.ADMIN);
                            log.info("Utilisateur {} promu ADMIN (app.admin.emails)", email);
                        }));
    }
}
