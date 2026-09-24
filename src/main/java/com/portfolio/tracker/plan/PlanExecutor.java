package com.portfolio.tracker.plan;

import com.portfolio.tracker.assetprice.MarketPriceLookup;
import com.portfolio.tracker.cash.CashMovementRepository;
import com.portfolio.tracker.cash.CashMovementService;
import com.portfolio.tracker.cash.CashMovementType;
import com.portfolio.tracker.cash.dto.CashMovementRequest;
import com.portfolio.tracker.notification.Formats;
import com.portfolio.tracker.notification.Notification;
import com.portfolio.tracker.notification.NotificationService;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.shared.MoneyConstants;
import com.portfolio.tracker.transaction.TransactionRepository;
import com.portfolio.tracker.transaction.TransactionService;
import com.portfolio.tracker.transaction.TransactionType;
import com.portfolio.tracker.transaction.dto.TransactionCreateRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

/**
 * Exécute les échéances échues d'un plan : chacune devient une transaction
 * (achat au cours de clôture du jour, prix estimé) ou un versement.
 *
 * Idempotent : une échéance déjà présente (référence {@code PLAN:<id>:<date>})
 * n'est pas recréée. Un plan est traité dans sa propre transaction, sous
 * verrou : l'historique n'est recalculé qu'une fois pour toutes ses échéances.
 */
@Component
@Slf4j
public class PlanExecutor {

    /** Garde-fou : un plan quotidien démarré il y a 5 ans n'est pas rattrapé d'un coup. */
    static final int MAX_EXECUTIONS_PER_RUN = 400;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final InvestmentPlanRepository planRepository;
    private final TransactionService transactionService;
    private final CashMovementService cashMovementService;
    private final TransactionRepository transactionRepository;
    private final CashMovementRepository cashMovementRepository;
    private final MarketPriceLookup priceLookup;
    private final NotificationService notificationService;
    private final TransactionTemplate txNew;

    public PlanExecutor(InvestmentPlanRepository planRepository,
            TransactionService transactionService,
            CashMovementService cashMovementService,
            TransactionRepository transactionRepository,
            CashMovementRepository cashMovementRepository,
            MarketPriceLookup priceLookup,
            NotificationService notificationService,
            PlatformTransactionManager transactionManager) {
        this.planRepository = planRepository;
        this.transactionService = transactionService;
        this.cashMovementService = cashMovementService;
        this.transactionRepository = transactionRepository;
        this.cashMovementRepository = cashMovementRepository;
        this.priceLookup = priceLookup;
        this.notificationService = notificationService;
        this.txNew = new TransactionTemplate(transactionManager);
        this.txNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Toutes les échéances dues, tous utilisateurs (job du soir, démarrage). */
    public void runDuePlans() {
        var ids = txNew.execute(s -> planRepository.findDueIds(LocalDate.now()));
        if (ids == null || ids.isEmpty()) {
            return;
        }
        log.info("Investissements programmés : {} plan(s) à exécuter", ids.size());
        for (UUID id : ids) {
            String previousError = lastError(id);
            int executed = run(id);
            if (executed > 0) {
                notifyExecuted(id, executed);
            }
            String error = lastError(id);
            if (error != null && !error.equals(previousError)) {
                notifyProblem(id, error);
            }
        }
    }

    private String lastError(UUID planId) {
        return txNew.execute(s -> planRepository.findById(planId).map(InvestmentPlan::getLastError).orElse(null));
    }

    /** Un nouveau problème (cours introuvable, montant insuffisant...) : prévenu une fois, pas à chaque essai. */
    private void notifyProblem(UUID planId, String error) {
        txNew.executeWithoutResult(s -> planRepository.findById(planId).ifPresent(plan ->
                notificationService.notify(plan.getPortfolio().getUser().getId(), Notification.Type.PLAN,
                        "⚠️ Investissement programmé : " + plan.getName(),
                        error + "\n\nL'échéance sera retentée automatiquement ; vérifie le plan si le problème persiste.",
                        "/portfolios/" + plan.getPortfolio().getId(), false)));
    }

    /** Prévient l'utilisateur des échéances exécutées par le job (pas lors d'une saisie : il le sait déjà). */
    private void notifyExecuted(UUID planId, int executed) {
        txNew.executeWithoutResult(s -> planRepository.findByIdForUpdate(planId).ifPresent(plan -> {
            String what = plan.getType() == InvestmentPlan.Type.DEPOSIT
                    ? "versement sur " + plan.getPortfolio().getName()
                    : plan.getName();
            String title = "Investissement programmé : " + Formats.eur(plan.getAmount()) + " — " + what;
            String body = executed == 1
                    ? "L'échéance du " + plan.getLastExecutionDate().format(DAY) + " a été ajoutée à « "
                            + plan.getPortfolio().getName() + " »" + (plan.getType() == InvestmentPlan.Type.BUY
                            ? " au cours de clôture du jour (prix estimé : l'import de ton relevé le remplacera)." : ".")
                    : executed + " échéances ont été ajoutées à « " + plan.getPortfolio().getName() + " ».";
            notificationService.notify(plan.getPortfolio().getUser().getId(), Notification.Type.PLAN, title, body,
                    "/portfolios/" + plan.getPortfolio().getId(), false);
        }));
    }

    /**
     * Exécute les échéances échues d'un plan. Ne lève jamais : un échec est
     * enregistré dans {@code lastError} et retenté à la prochaine exécution.
     *
     * @return nombre d'échéances exécutées
     */
    public int run(UUID planId) {
        try {
            Integer executed = txNew.execute(s -> runLocked(planId));
            return executed != null ? executed : 0;
        } catch (RuntimeException e) {
            // Tout ce qui a été fait dans ce passage est annulé : on note l'erreur à part
            log.warn("Plan {} : exécution interrompue : {}", planId, e.getMessage());
            txNew.executeWithoutResult(s -> planRepository.findById(planId)
                    .ifPresent(plan -> plan.setLastError(truncate(e.getMessage()))));
            return 0;
        }
    }

    private int runLocked(UUID planId) {
        InvestmentPlan plan = planRepository.findByIdForUpdate(planId).orElse(null);
        if (plan == null || !plan.isActive()) {
            return 0;
        }
        LocalDate today = LocalDate.now();
        int executed = 0;
        String problem = null; // dernière échéance sautée ou erreur à retenter
        while (plan.getNextExecutionDate() != null && !plan.getNextExecutionDate().isAfter(today)
                && executed < MAX_EXECUTIONS_PER_RUN) {
            LocalDate day = plan.getNextExecutionDate();
            Outcome outcome = executeOnce(plan, day);
            if (outcome.problem() != null) {
                problem = outcome.problem();
            }
            if (outcome.kind() == Kind.RETRY_LATER) {
                break;
            }
            if (outcome.kind() == Kind.EXECUTED) {
                executed++;
                plan.setLastExecutionDate(day);
            }
            plan.advance();
        }
        plan.setLastError(problem);
        if (executed > 0) {
            log.info("Plan {} ({}) : {} échéance(s) exécutée(s), prochaine le {}", plan.getId(), plan.getName(),
                    executed, plan.getNextExecutionDate());
        }
        return executed;
    }

    private enum Kind { EXECUTED, ALREADY_DONE, SKIPPED, RETRY_LATER }

    /** Résultat d'une échéance, avec le message à afficher en cas de saut ou d'échec. */
    private record Outcome(Kind kind, String problem) {
        static final Outcome EXECUTED = new Outcome(Kind.EXECUTED, null);
        static final Outcome ALREADY_DONE = new Outcome(Kind.ALREADY_DONE, null);
    }

    private Outcome executeOnce(InvestmentPlan plan, LocalDate day) {
        String ref = plan.executionRef(day);
        if (transactionRepository.existsByExternalRef(ref) || cashMovementRepository.existsByExternalRef(ref)) {
            return Outcome.ALREADY_DONE;
        }
        Portfolio portfolio = plan.getPortfolio();
        UUID userId = portfolio.getUser().getId();

        if (plan.getType() == InvestmentPlan.Type.DEPOSIT) {
            if (!portfolio.isCashTracking()) {
                return new Outcome(Kind.RETRY_LATER, "Active le suivi des liquidités de « " + portfolio.getName()
                        + " » pour que les versements programmés s'exécutent");
            }
            cashMovementService.create(portfolio.getId(), new CashMovementRequest(CashMovementType.DEPOSIT,
                    plan.getAmount(), day, "Versement programmé"), userId, ref);
            return Outcome.EXECUTED;
        }

        Optional<BigDecimal> price = priceLookup.priceInEur(plan.getSymbol(), day);
        if (price.isEmpty()) {
            return new Outcome(Kind.RETRY_LATER, "Cours de " + plan.getSymbol() + " indisponible pour le "
                    + day.format(DAY) + " : nouvel essai ce soir");
        }
        BigDecimal unitPrice = price.get().setScale(8, RoundingMode.HALF_UP);
        BigDecimal net = plan.getAmount().subtract(plan.getFees());
        BigDecimal quantity = plan.isFractional()
                ? net.divide(unitPrice, 8, RoundingMode.DOWN)
                : net.divide(unitPrice, 0, RoundingMode.DOWN);
        if (quantity.signum() == 0) {
            return new Outcome(Kind.SKIPPED, "Échéance du " + day.format(DAY) + " sautée : " + net
                    + " € ne suffisent pas pour une part (" + unitPrice.setScale(2, RoundingMode.HALF_UP) + " €)");
        }
        transactionService.create(portfolio.getId(), new TransactionCreateRequest(
                plan.getSymbol(), TransactionType.BUY, quantity, unitPrice,
                plan.getFees().setScale(MoneyConstants.MONEY_SCALE, MoneyConstants.ROUNDING),
                MoneyConstants.BASE_CURRENCY, day.atStartOfDay(), "Investissement programmé (prix estimé)"),
                userId, ref);
        return Outcome.EXECUTED;
    }

    private static String truncate(String message) {
        String m = message == null ? "Erreur inconnue" : message;
        return m.length() > 500 ? m.substring(0, 500) : m;
    }
}
