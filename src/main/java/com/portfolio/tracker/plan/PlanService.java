package com.portfolio.tracker.plan;

import com.portfolio.tracker.marketdata.MarketDataProvider;
import com.portfolio.tracker.marketdata.MarketQuote;
import com.portfolio.tracker.plan.dto.PlanRequest;
import com.portfolio.tracker.plan.dto.PlanResponse;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.portfolio.PortfolioRules;
import com.portfolio.tracker.shared.exception.BadRequestException;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Plans d'investissement. Toute création ou modification est suivie de
 * l'exécution des échéances déjà échues (un plan démarré dans le passé
 * recrée son historique), dans une transaction séparée : le plan doit être
 * enregistré pour que l'exécuteur le voie.
 */
@Service
public class PlanService {

    private final InvestmentPlanRepository planRepository;
    private final PortfolioRepository portfolioRepository;
    private final MarketDataProvider marketDataProvider;
    private final PlanExecutor executor;
    private final PlanMapper mapper;
    private final TransactionTemplate tx;

    public PlanService(InvestmentPlanRepository planRepository,
            PortfolioRepository portfolioRepository,
            MarketDataProvider marketDataProvider,
            PlanExecutor executor,
            PlanMapper mapper,
            PlatformTransactionManager transactionManager) {
        this.planRepository = planRepository;
        this.portfolioRepository = portfolioRepository;
        this.marketDataProvider = marketDataProvider;
        this.executor = executor;
        this.mapper = mapper;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ================================================================ lecture

    public List<PlanResponse> findAll(UUID userId) {
        return tx.execute(s -> planRepository.findByUserId(userId).stream().map(mapper::toResponse).toList());
    }

    public List<PlanResponse> findByPortfolio(UUID portfolioId, UUID userId) {
        return tx.execute(s -> {
            ownedPortfolio(portfolioId, userId);
            return planRepository.findByPortfolioIdAndUserId(portfolioId, userId).stream().map(mapper::toResponse).toList();
        });
    }

    // ============================================================= écriture

    public PlanResponse create(UUID portfolioId, PlanRequest request, UUID userId) {
        UUID planId = tx.execute(s -> {
            Portfolio portfolio = ownedPortfolio(portfolioId, userId);
            validate(portfolio, request);
            InvestmentPlan plan = InvestmentPlan.builder()
                    .portfolio(portfolio)
                    .type(request.type())
                    .frequency(request.frequency())
                    .startDate(request.startDate())
                    .build();
            applyTarget(plan, request);
            applyEditable(plan, request);
            plan.setNextExecutionDate(plan.computeNextDate());
            return planRepository.save(plan).getId();
        });
        executor.run(planId);
        return reload(planId, userId);
    }

    public PlanResponse update(UUID planId, PlanRequest request, UUID userId) {
        tx.executeWithoutResult(s -> {
            InvestmentPlan plan = ownedPlan(planId, userId);
            validate(plan.getPortfolio(), request);
            boolean structureChanged = plan.getType() != request.type()
                    || !Objects.equals(plan.getSymbol(), normalizedSymbol(request))
                    || plan.getFrequency() != request.frequency()
                    || !plan.getStartDate().equals(request.startDate());
            if (structureChanged) {
                if (plan.getOccurrences() > 0) {
                    throw new BadRequestException("Ce plan a déjà des échéances : pour changer d'actif, de fréquence "
                            + "ou de date de début, mets-le en pause et crée-en un nouveau");
                }
                plan.setType(request.type());
                plan.setFrequency(request.frequency());
                plan.setStartDate(request.startDate());
                applyTarget(plan, request);
            }
            boolean resumed = !plan.isActive() && !Boolean.FALSE.equals(request.active());
            applyEditable(plan, request);
            plan.setNextExecutionDate(plan.computeNextDate());
            if (resumed) {
                skipPausedPeriod(plan);
            }
        });
        executor.run(planId);
        return reload(planId, userId);
    }

    /** Les transactions déjà générées restent : ce sont de vraies opérations passées. */
    public void delete(UUID planId, UUID userId) {
        tx.executeWithoutResult(s -> planRepository.delete(ownedPlan(planId, userId)));
    }

    // ================================================================ règles

    private void validate(Portfolio portfolio, PlanRequest request) {
        if (request.type() == InvestmentPlan.Type.BUY) {
            if (PortfolioRules.holdsOnlyCash(portfolio.getType())) {
                throw new BadRequestException("Un livret ne détient pas d'actifs : programme plutôt un versement");
            }
            if (request.symbol() == null || request.symbol().isBlank()) {
                throw new BadRequestException("Choisis l'actif à acheter");
            }
        } else if (!portfolio.isCashTracking()) {
            throw new BadRequestException("Active le suivi des liquidités de ce portefeuille pour programmer des versements");
        }
        BigDecimal fees = request.fees() == null ? BigDecimal.ZERO : request.fees();
        if (fees.compareTo(request.amount()) >= 0) {
            throw new BadRequestException("Les frais doivent être inférieurs au montant");
        }
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new BadRequestException("La date de fin doit suivre la date de début");
        }
    }

    /** Actif acheté : symbole canonique et nom renvoyés par Yahoo. */
    private void applyTarget(InvestmentPlan plan, PlanRequest request) {
        if (request.type() == InvestmentPlan.Type.DEPOSIT) {
            plan.setSymbol(null);
            plan.setName("Versement");
            return;
        }
        MarketQuote quote = marketDataProvider.getQuote(normalizedSymbol(request))
                .orElseThrow(() -> new BadRequestException("Actif introuvable : " + request.symbol()));
        plan.setSymbol(quote.symbol());
        plan.setName(quote.longName() != null ? quote.longName() : quote.symbol());
    }

    private static void applyEditable(InvestmentPlan plan, PlanRequest request) {
        plan.setAmount(request.amount());
        plan.setFees(request.fees() == null ? BigDecimal.ZERO : request.fees());
        plan.setEndDate(request.endDate());
        plan.setFractional(!Boolean.FALSE.equals(request.fractional()));
        plan.setActive(!Boolean.FALSE.equals(request.active()));
    }

    /**
     * Reprise après une pause : les échéances de la période de pause ne sont
     * pas rattrapées (l'argent n'a pas été investi). Celle du jour s'exécute.
     */
    private static void skipPausedPeriod(InvestmentPlan plan) {
        LocalDate today = LocalDate.now();
        while (plan.getNextExecutionDate() != null && plan.getNextExecutionDate().isBefore(today)) {
            plan.advance();
        }
    }

    private static String normalizedSymbol(PlanRequest request) {
        return request.type() == InvestmentPlan.Type.DEPOSIT || request.symbol() == null
                ? null : request.symbol().trim().toUpperCase();
    }

    private PlanResponse reload(UUID planId, UUID userId) {
        return tx.execute(s -> mapper.toResponse(ownedPlan(planId, userId)));
    }

    private Portfolio ownedPortfolio(UUID portfolioId, UUID userId) {
        return portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio non accessible"));
    }

    private InvestmentPlan ownedPlan(UUID planId, UUID userId) {
        return planRepository.findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan non accessible"));
    }
}
