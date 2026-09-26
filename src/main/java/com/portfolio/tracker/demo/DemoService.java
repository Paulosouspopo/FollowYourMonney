package com.portfolio.tracker.demo;

import com.portfolio.tracker.auth.AuthService;
import com.portfolio.tracker.shared.security.SecureTokens;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.shared.exception.BadRequestException;
import com.portfolio.tracker.snapshot.PortfolioSnapshotRepository;
import com.portfolio.tracker.user.User;
import com.portfolio.tracker.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Mode démo : un compte invité par visiteur, rempli d'un patrimoine fictif
 * ({@link DemoSeeder}), connecté aussitôt, supprimé 24 h après sa création.
 * Aucun email n'est jamais envoyé (domaine .invalid).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DemoService {

    static final int LIFETIME_HOURS = 24;
    /** Garde-fou : au-delà, le mode démo est momentanément fermé. */
    static final int MAX_LIVE_DEMOS = 300;

    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final PasswordEncoder passwordEncoder;
    private final DemoSeeder seeder;
    private final AuthService authService;
    private final TransactionTemplate tx;

    public AuthService.Session start(AuthService.Device device) {
        if (userRepository.countByDemoTrue() >= MAX_LIVE_DEMOS) {
            throw new BadRequestException("Le mode démo est très demandé : réessaie dans quelques minutes");
        }
        String id = UUID.randomUUID().toString();
        User user = userRepository.save(User.builder()
                .email("demo-" + id + "@demo.invalid")
                .username("Invité")
                .password(passwordEncoder.encode(SecureTokens.generate()))
                .emailVerified(true)
                .demo(true)
                .build());
        try {
            UUID pea = seeder.seed(user.getId());
            seeder.seedPlan(pea, user.getId());
        } catch (RuntimeException e) {
            log.error("Démo : remplissage échoué, compte supprimé : {}", e.getMessage(), e);
            delete(user);
            throw new BadRequestException("La démo n'a pas pu être préparée (cours indisponibles) : réessaie dans un instant");
        }
        return authService.openDemoSession(user, device);
    }

    /** Supprime les comptes invités de plus de 24 h. */
    public int purgeExpired() {
        List<User> expired = userRepository.findByDemoTrueAndCreatedAtBefore(LocalDateTime.now().minusHours(LIFETIME_HOURS));
        expired.forEach(this::delete);
        return expired.size();
    }

    private void delete(User user) {
        tx.executeWithoutResult(s -> {
            // Les snapshots référencent le portefeuille sans cascade (comme à la suppression d'un portefeuille)
            portfolioRepository.findByUserId(user.getId()).stream().map(Portfolio::getId)
                    .forEach(snapshotRepository::deleteByPortfolioId);
            userRepository.deleteById(user.getId());
        });
    }
}
