package com.portfolio.tracker.snapshot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Recalcule l'historique des portefeuilles modifiés, APRÈS le commit de la
 * transaction métier (sinon le recalcul, qui tourne dans sa propre transaction,
 * ne verrait pas les données qu'on vient d'écrire).
 *
 * Les événements d'une même transaction sont regroupés par portefeuille en
 * gardant la date la plus ancienne : un import de 500 transactions ne
 * déclenche qu'UN recalcul par portefeuille.
 *
 * Synchrone pour l'instant (dev). Pour passer en asynchrone : exécuter
 * {@link #refreshDirty} via un executor / @Async, sans rien changer d'autre.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PortfolioHistoryListener {

    private final PortfolioHistoryService historyService;

    @EventListener
    public void onChange(PortfolioHistoryChangedEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            historyService.refresh(event.portfolioId(), event.from());
            return;
        }
        PendingRefresh pending = (PendingRefresh) TransactionSynchronizationManager.getResource(this);
        if (pending == null) {
            pending = new PendingRefresh();
            TransactionSynchronizationManager.bindResource(this, pending);
            TransactionSynchronizationManager.registerSynchronization(pending);
        }
        pending.add(event);
    }

    private void refreshDirty(Map<UUID, Optional<LocalDate>> dirty) {
        dirty.forEach((portfolioId, from) -> historyService.refresh(portfolioId, from.orElse(null)));
    }

    /** Portefeuilles à recalculer pour la transaction courante. */
    private final class PendingRefresh implements TransactionSynchronization {

        /** Optional.empty() = recalcul complet. */
        private final Map<UUID, Optional<LocalDate>> dirty = new LinkedHashMap<>();

        void add(PortfolioHistoryChangedEvent event) {
            Optional<LocalDate> from = Optional.ofNullable(event.from());
            dirty.merge(event.portfolioId(), from, (a, b) -> a.isEmpty() || b.isEmpty()
                    ? Optional.empty()
                    : Optional.of(a.get().isBefore(b.get()) ? a.get() : b.get()));
        }

        @Override
        public void afterCommit() {
            refreshDirty(dirty);
        }

        @Override
        public void afterCompletion(int status) {
            TransactionSynchronizationManager.unbindResourceIfPossible(PortfolioHistoryListener.this);
        }
    }
}
