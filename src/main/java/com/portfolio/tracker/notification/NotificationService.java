package com.portfolio.tracker.notification;

import com.portfolio.tracker.notification.push.PushService;
import com.portfolio.tracker.shared.TimeZones;
import com.portfolio.tracker.shared.exception.BadRequestException;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import com.portfolio.tracker.shared.mail.EmailSender;
import com.portfolio.tracker.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Point d'entrée unique pour prévenir un utilisateur : la notification est
 * toujours enregistrée dans la boîte de réception de l'app, et envoyée en
 * plus par email et/ou en push si demandé. Le push respecte les préférences
 * de l'utilisateur (désactivé, heures calmes) et part après le commit.
 */
@Service
@Slf4j
public class NotificationService {

    private final NotificationRepository repository;
    private final UserRepository userRepository;
    private final EmailSender emailSender;
    private final PushService pushService;
    private final NotificationPreferencesRepository preferencesRepository;
    private final String frontendUrl;

    public NotificationService(NotificationRepository repository, UserRepository userRepository,
            EmailSender emailSender, PushService pushService, NotificationPreferencesRepository preferencesRepository,
            @Value("${app.frontend-url}") String frontendUrl) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.emailSender = emailSender;
        this.pushService = pushService;
        this.preferencesRepository = preferencesRepository;
        this.frontendUrl = frontendUrl;
    }

    /** Boîte de réception + push (selon préférences) + email si demandé. */
    @Transactional
    public Notification notify(UUID userId, Notification.Type type, String title, String body, String link,
                               boolean email) {
        return notify(userId, type, title, body, link, email, true);
    }

    @Transactional
    public Notification notify(UUID userId, Notification.Type type, String title, String body, String link,
                               boolean email, boolean push) {
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
        if (push && pushAllowed(userId)) {
            String tag = type.name() + ":" + saved.getId();
            afterCommit(() -> pushService.send(userId, saved.getTitle(), saved.getBody(), link, tag));
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public NotificationPreferences preferences(UUID userId) {
        return preferencesRepository.findById(userId).orElseGet(() -> NotificationPreferences.defaults(userId));
    }

    @Transactional
    public NotificationPreferences updatePreferences(UUID userId, boolean pushEnabled, Integer quietStart,
                                                     Integer quietEnd) {
        if ((quietStart == null) != (quietEnd == null)) {
            throw new BadRequestException("Heures calmes : indiquer le début et la fin, ou aucune des deux");
        }
        NotificationPreferences prefs = preferencesRepository.findById(userId)
                .orElseGet(() -> NotificationPreferences.defaults(userId));
        prefs.setPushEnabled(pushEnabled);
        prefs.setQuietStart(quietStart);
        prefs.setQuietEnd(quietEnd);
        return preferencesRepository.save(prefs);
    }

    private boolean pushAllowed(UUID userId) {
        NotificationPreferences prefs = preferences(userId);
        return prefs.isPushEnabled() && !prefs.isQuiet(TimeZones.nowForUser().toLocalTime());
    }

    /** Le push (HTTP) ne part que si la notification est bien enregistrée. */
    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
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
