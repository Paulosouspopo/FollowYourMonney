package com.portfolio.tracker.notification;

import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import com.portfolio.tracker.shared.mail.EmailSender;
import com.portfolio.tracker.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Point d'entrée unique pour prévenir un utilisateur : la notification est
 * toujours enregistrée dans la boîte de réception de l'app, et envoyée en
 * plus par email si demandé. (Les notifications push viendront s'ajouter ici.)
 */
@Service
@Slf4j
public class NotificationService {

    private final NotificationRepository repository;
    private final UserRepository userRepository;
    private final EmailSender emailSender;
    private final String frontendUrl;

    public NotificationService(NotificationRepository repository, UserRepository userRepository,
            EmailSender emailSender, @Value("${app.frontend-url}") String frontendUrl) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.emailSender = emailSender;
        this.frontendUrl = frontendUrl;
    }

    @Transactional
    public Notification notify(UUID userId, Notification.Type type, String title, String body, String link,
                               boolean email) {
        Notification saved = repository.save(Notification.builder()
                .userId(userId)
                .type(type)
                .title(truncate(title, 200))
                .body(truncate(body, 4000))
                .link(link)
                .createdAt(LocalDateTime.now())
                .build());
        if (email) {
            userRepository.findById(userId).ifPresent(user -> emailSender.send(user.getEmail(),
                    title + " — FollowYourMoney",
                    body + "\n\n" + frontendUrl + (link != null ? link : "/")));
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Notification> recent(UUID userId, int limit) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, Math.min(Math.max(limit, 1), 200)));
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markRead(UUID id, UUID userId) {
        Notification n = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification non accessible"));
        if (n.getReadAt() == null) {
            n.setReadAt(LocalDateTime.now());
        }
    }

    @Transactional
    public void markAllRead(UUID userId) {
        repository.markAllRead(userId, LocalDateTime.now());
    }

    /** Purge : la boîte de réception n'est pas un historique permanent. */
    @Transactional
    public int purgeOlderThan(LocalDateTime before) {
        return repository.deleteOlderThan(before);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
