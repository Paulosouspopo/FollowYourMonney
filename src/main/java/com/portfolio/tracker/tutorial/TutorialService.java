package com.portfolio.tracker.tutorial;

import com.portfolio.tracker.shared.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Progression dans les tutoriels (visites guidées du front). Les clés sont
 * libres (définies par le front) mais bornées : format et nombre.
 */
@Service
@RequiredArgsConstructor
public class TutorialService {

    private static final Pattern KEY = Pattern.compile("[a-z0-9-]{1,40}");
    private static final int MAX_KEYS = 40;

    private final TutorialStateRepository repository;

    public record State(boolean autoEnabled, List<String> completed) {
    }

    @Transactional(readOnly = true)
    public State get(UUID userId) {
        return repository.findById(userId).map(TutorialService::toState).orElse(new State(true, List.of()));
    }

    @Transactional
    public State complete(UUID userId, String key) {
        if (!KEY.matcher(key).matches()) {
            throw new BadRequestException("Tutoriel inconnu");
        }
        TutorialState state = load(userId);
        Set<String> keys = state.completedKeys();
        if (keys.add(key) && keys.size() > MAX_KEYS) {
            throw new BadRequestException("Trop de tutoriels");
        }
        state.setCompletedKeys(keys);
        return toState(repository.save(state));
    }

    @Transactional
    public State setAutoEnabled(UUID userId, boolean enabled) {
        TutorialState state = load(userId);
        state.setAutoEnabled(enabled);
        return toState(repository.save(state));
    }

    /** Tout revoir : visites remises à zéro, affichage automatique réactivé. */
    @Transactional
    public State reset(UUID userId) {
        TutorialState state = load(userId);
        state.setCompleted("");
        state.setAutoEnabled(true);
        return toState(repository.save(state));
    }

    private TutorialState load(UUID userId) {
        return repository.findById(userId).orElseGet(() -> new TutorialState(userId));
    }

    private static State toState(TutorialState s) {
        return new State(s.isAutoEnabled(), List.copyOf(s.completedKeys()));
    }
}
