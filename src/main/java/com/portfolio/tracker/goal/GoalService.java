package com.portfolio.tracker.goal;

import com.portfolio.tracker.dashboard.PortfolioValuationService;
import com.portfolio.tracker.dashboard.dto.PortfolioValuation;
import com.portfolio.tracker.dashboard.dto.ValuationResult;
import com.portfolio.tracker.goal.dto.GoalRequest;
import com.portfolio.tracker.goal.dto.GoalResponse;
import com.portfolio.tracker.plan.InvestmentPlan;
import com.portfolio.tracker.plan.InvestmentPlanRepository;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.shared.MoneyConstants;
import com.portfolio.tracker.shared.exception.BadRequestException;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GoalService {

    private static final int MAX_GOALS = 20;

    private final GoalRepository repository;
    private final PortfolioRepository portfolioRepository;
    private final InvestmentPlanRepository planRepository;
    private final PortfolioValuationService valuationService;

    public List<GoalResponse> findAll(UUID userId) {
        List<Goal> goals = repository.findByUserId(userId);
        if (goals.isEmpty()) {
            return List.of();
        }
        Context ctx = context(userId);
        return goals.stream().map(g -> toResponse(g, ctx)).toList();
    }

    @Transactional
    public GoalResponse create(GoalRequest request, UUID userId) {
        if (repository.countByUserId(userId) >= MAX_GOALS) {
            throw new BadRequestException("Tu as déjà " + MAX_GOALS + " objectifs : supprimes-en un avant d'en créer");
        }
        Goal goal = Goal.builder().userId(userId).build();
        apply(goal, request, userId);
        return toResponse(repository.save(goal), context(userId));
    }

    @Transactional
    public GoalResponse update(UUID id, GoalRequest request, UUID userId) {
        Goal goal = owned(id, userId);
        apply(goal, request, userId);
        return toResponse(goal, context(userId));
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        repository.delete(owned(id, userId));
    }

    private void apply(Goal goal, GoalRequest r, UUID userId) {
        if (r.targetDate() != null && !r.targetDate().isAfter(LocalDate.now())) {
            throw new BadRequestException("L'échéance doit être dans le futur");
        }
        goal.setName(r.name().trim());
        goal.setTargetAmount(r.targetAmount().setScale(MoneyConstants.MONEY_SCALE, RoundingMode.HALF_UP));
        goal.setTargetDate(r.targetDate());
        goal.setPortfolio(r.portfolioId() == null ? null : portfolioRepository.findByIdAndUserId(r.portfolioId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio non accessible")));
    }

    private Goal owned(UUID id, UUID userId) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Objectif introuvable"));
    }

    /** Valorisation et versements programmés, calculés une fois pour tous les objectifs. */
    private record Context(BigDecimal totalValue, Map<UUID, BigDecimal> valueByPortfolio,
                           BigDecimal totalMonthly, Map<UUID, BigDecimal> monthlyByPortfolio) {
    }

    private Context context(UUID userId) {
        ValuationResult v = valuationService.valuate(userId, null, null);
        Map<UUID, BigDecimal> values = v.getPortfolios().stream()
                .collect(Collectors.toMap(PortfolioValuation::getPortfolioId, PortfolioValuation::getCurrentValueEur));
        Map<UUID, BigDecimal> monthly = planRepository.findByUserId(userId).stream()
                .filter(p -> p.isActive() && p.getNextExecutionDate() != null)
                .collect(Collectors.groupingBy(p -> p.getPortfolio().getId(),
                        Collectors.reducing(BigDecimal.ZERO, (InvestmentPlan p) -> p.getFrequency().monthly(p.getAmount()),
                                BigDecimal::add)));
        return new Context(v.getTotalValueEur(), values,
                monthly.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add), monthly);
    }

    private static GoalResponse toResponse(Goal g, Context ctx) {
        UUID pid = g.getPortfolio() != null ? g.getPortfolio().getId() : null;
        BigDecimal current = pid == null ? ctx.totalValue() : ctx.valueByPortfolio().getOrDefault(pid, BigDecimal.ZERO);
        BigDecimal monthly = pid == null ? ctx.totalMonthly() : ctx.monthlyByPortfolio().getOrDefault(pid, BigDecimal.ZERO);
        BigDecimal progress = current.signum() <= 0 ? BigDecimal.ZERO
                : current.multiply(BigDecimal.valueOf(100)).divide(g.getTargetAmount(), 2, RoundingMode.HALF_UP)
                        .min(BigDecimal.valueOf(100));
        return new GoalResponse(g.getId(), g.getName(), g.getTargetAmount(), g.getTargetDate(), pid,
                g.getPortfolio() != null ? g.getPortfolio().getName() : null,
                current.setScale(MoneyConstants.MONEY_SCALE, RoundingMode.HALF_UP),
                monthly.setScale(MoneyConstants.MONEY_SCALE, RoundingMode.HALF_UP), progress);
    }
}
