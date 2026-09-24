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
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertRuleService {

    /** Au-delà, une variation n'a plus de sens (et trahit une saisie en € au lieu de %). */
    private static final BigDecimal MAX_PERCENT = new BigDecimal("1000");
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

    private void apply(AlertRule rule, AlertRuleRequest r, UUID userId) {
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
        rule.setCondition(r.condition());
        rule.setThreshold(r.threshold());
        if (r.condition().isPercentage()) {
            if (r.threshold().compareTo(MAX_PERCENT) > 0) {
                throw new BadRequestException("Variation trop grande : le seuil est en %");
            }
            rule.setPeriod(r.period() != null ? r.period() : AlertRule.Period.DAY);
        } else {
            rule.setPeriod(null);
        }
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
                r.isEnabled(),
                r.getLastTriggeredAt(),
                AlertRuleDescriber.describe(r));
    }
}
