package com.portfolio.tracker.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Purge quotidienne des sessions et liens expirés (ils ne servent plus à rien). */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class AuthCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountTokenRepository accountTokenRepository;

    @Scheduled(cron = "${auth.cleanup.cron:0 15 4 * * *}")
    @Transactional
    public void purgeExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        int sessions = refreshTokenRepository.deleteExpired(now);
        int links = accountTokenRepository.deleteExpired(now);
        log.info("Purge : {} session(s) et {} lien(s) expiré(s) supprimés", sessions, links);
    }
}
