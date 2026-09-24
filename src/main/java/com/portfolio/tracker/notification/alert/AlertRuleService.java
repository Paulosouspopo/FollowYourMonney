package com.portfolio.tracker.notification.alert;

import com.portfolio.tracker.marketdata.MarketDataProvider;
import com.portfolio.tracker.marketdata.MarketQuote;
import com.portfolio.tracker.notification.dto.AlertRuleRequest;
import com.portfolio.tracker.notification.dto.AlertRuleResponse;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.shared.exception.BadRequestException;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertRuleService {

    /** Au-delà, une variation n'a plus de sens (et trahit une saisie en € au lieu de %). */
    private static final BigDecimal MAX_PERCENT = new BigDecimal("1000");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int MAX_RULES_PER_USER = 50;

    private final AlertRuleRepository repository;
    private final PortfolioRepository portfolioRepository;
    private final MarketDataProvider marketDataProvider;

    public List<AlertRuleResponse> findAll(UUID userId) {
        return repository.findByUserId(userId).stream().map(AlertRuleService::toResponse).toList();
    }

    @Transactional
    public AlertRuleResponse create(AlertRuleRequest request, UUID userId) {
        if (repository.findByUserId(userId).size() >= MAX_RULES_PER_USER) {
            throw new BadRequestException("Tu as déjà " + MAX_RULES_PER_USER + " alertes : supprime-en avant d'en créer");
        }
        AlertRule rule = AlertRule.builder().userId(userId).build();
        apply(rule, request, userId);
        return toResponse(repository.save(rule));
    }

    /** Une règle modifiée est réarmée : elle peut se déclencher à nouveau. */
    @Transactional
    public AlertRuleResponse update(UUID id, AlertRuleRequest request, UUID userId) {
        AlertRule rule = owned(id, userId);
        apply(rule, request, userId);
        rule.setArmed(true);
        return toResponse(rule);
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        repository.delete(owned(id, userId));
    }

    /** Sourdine rapide depuis la liste (null = réactiver). */
    @Transactional
    public AlertRuleResponse mute(UUID id, LocalDateTime until, UUID userId) {
        AlertRule rule = owned(id, userId);
        rule.setMutedUntil(until);
        return toResponse(rule);
    }

    private void apply(AlertRule rule, AlertRuleRequest r, UUID userId) {
        AlertRule.Condition c = r.condition();
        if (c.isExtreme() && r.scope() != AlertRule.Scope.ASSET) {
            throw new BadRequestException("Plus haut / plus bas : uniquement sur un actif");
        }
        if (c == AlertRule.Condition.WEIGHT_ABOVE && r.scope() == AlertRule.Scope.GLOBAL) {
            throw new BadRequestException("Poids : choisis un actif ou un portefeuille");
        }
        rule.setScope(r.scope());
        rule.setPortfolio(null);
        rule.setSymbol(null);
        rule.setAssetName(null);
        switch (r.scope()) {
            case PORTFOLIO -> {
                if (r.portfolioId() == null) {
                    throw new BadRequestException("Choisis le portefeuille à surveiller");
                }
                Portfolio portfolio = portfolioRepository.findByIdAndUserId(r.portfolioId(), userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Portfolio non accessible"));
                rule.setPortfolio(portfolio);
            }
            case ASSET -> {
                if (r.symbol() == null || r.symbol().isBlank()) {
                    throw new BadRequestException("Choisis l'actif à surveiller");
                }
                MarketQuote quote = marketDataProvider.getQuote(r.symbol().trim().toUpperCase())
                        .orElseThrow(() -> new BadRequestException("Actif introuvable : " + r.symbol()));
                rule.setSymbol(quote.symbol());
                rule.setAssetName(quote.longName() != null ? quote.longName() : quote.symbol());
            }
            case GLOBAL -> {
                // rien à préciser
            }
        }
        rule.setCondition(c);
        if (c.hasThreshold()) {
            if (r.threshold() == null || r.threshold().signum() <= 0) {
                throw new BadRequestException("Le seuil doit être strictement positif");
            }
            BigDecimal max = c == AlertRule.Condition.WEIGHT_ABOVE ? HUNDRED : MAX_PERCENT;
            if (c.isPercentage() && r.threshold().compareTo(max) > 0) {
                throw new BadRequestException("Seuil trop grand : il est en % (maximum " + max.toPlainString() + ")");
            }
            rule.setThreshold(r.threshold());
        } else {
            rule.setThreshold(BigDecimal.ZERO);
        }
        if (c.isVariation()) {
            rule.setPeriod(r.period() != null ? r.period() : AlertRule.Period.DAY);
        } else if (c.isExtreme()) {
            rule.setPeriod(r.period() != null && r.period() != AlertRule.Period.DAY ? r.period() : AlertRule.Period.YEAR);
        } else {
            rule.setPeriod(null);
        }
        rule.setLabel(r.label() != null && !r.label().isBlank() ? r.label().trim() : null);
        rule.setNotifyPush(!Boolean.FALSE.equals(r.notifyPush()));
        rule.setMutedUntil(r.mutedUntil());
        rule.setNotifyEmail(r.notifyEmail());
        rule.setEnabled(!Boolean.FALSE.equals(r.enabled()));
    }

    private AlertRule owned(UUID id, UUID userId) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Alerte non accessible"));
    }

    static AlertRuleResponse toResponse(AlertRule r) {
        return new AlertRuleResponse(
                r.getId(),
                r.getScope(),
                r.getPortfolio() != null ? r.getPortfolio().getId() : null,
                r.getPortfolio() != null ? r.getPortfolio().getName() : null,
                r.getSymbol(),
                r.getAssetName(),
                r.getCondition(),
                r.getThreshold(),
                r.getPeriod(),
                r.isNotifyEmail(),
                r.isNotifyPush(),
                r.isEnabled(),
                r.getLabel(),
                r.getMutedUntil(),
                r.getLastTriggeredAt(),
                AlertRuleDescriber.describe(r));
    }
}
