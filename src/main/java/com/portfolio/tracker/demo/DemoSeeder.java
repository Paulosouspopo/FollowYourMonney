package com.portfolio.tracker.demo;

import com.portfolio.tracker.asset.AssetType;
import com.portfolio.tracker.asset.dto.AssetResponse;
import com.portfolio.tracker.asset.manual.ManualAssetRequest;
import com.portfolio.tracker.asset.manual.ManualAssetService;
import com.portfolio.tracker.asset.manual.ValuationRequest;
import com.portfolio.tracker.assetprice.MarketPriceLookup;
import com.portfolio.tracker.cash.CashMovementService;
import com.portfolio.tracker.cash.CashMovementType;
import com.portfolio.tracker.cash.dto.CashMovementRequest;
import com.portfolio.tracker.goal.GoalService;
import com.portfolio.tracker.goal.dto.GoalRequest;
import com.portfolio.tracker.plan.InvestmentPlan;
import com.portfolio.tracker.plan.PlanFrequency;
import com.portfolio.tracker.plan.PlanService;
import com.portfolio.tracker.plan.dto.PlanRequest;
import com.portfolio.tracker.portfolio.PortfolioService;
import com.portfolio.tracker.portfolio.PortfolioType;
import com.portfolio.tracker.portfolio.dto.PortfolioCreateRequest;
import com.portfolio.tracker.transaction.TransactionService;
import com.portfolio.tracker.transaction.TransactionType;
import com.portfolio.tracker.transaction.dto.TransactionCreateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Patrimoine fictif mais réaliste d'un compte invité, sur trois ans, aux vrais
 * cours de clôture : PEA en versements programmés sur un ETF monde + actions
 * françaises, crypto, Livret A, assurance-vie avec un fonds non coté,
 * un investissement programmé et un objectif. Tout passe par les services
 * métier (mêmes règles qu'une saisie), dans une seule transaction : un seul
 * recalcul de l'historique par portefeuille, après validation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DemoSeeder {

    private final PortfolioService portfolioService;
    private final TransactionService transactionService;
    private final CashMovementService cashMovementService;
    private final ManualAssetService manualAssetService;
    private final PlanService planService;
    private final GoalService goalService;
    private final MarketPriceLookup priceLookup;

    /** @return le PEA, qui reçoit ensuite l'investissement programmé ({@link #seedPlan}) */
    @Transactional
    public UUID seed(UUID userId) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusMonths(36).withDayOfMonth(5);

        // PEA : 200 € par mois sur un ETF monde, et quelques actions françaises
        UUID pea = portfolio(userId, "PEA (démo)", PortfolioType.PEA, false, null, start.minusMonths(2));
        for (LocalDate d = start; d.isBefore(today); d = d.plusMonths(1)) {
            buyAmount(pea, "CW8.PA", new BigDecimal("200"), businessDay(d), userId);
        }
        buyQuantity(pea, "AI.PA", new BigDecimal("10"), businessDay(today.minusMonths(30)), userId);
        buyQuantity(pea, "TTE.PA", new BigDecimal("20"), businessDay(today.minusMonths(26)), userId);
        buyQuantity(pea, "MC.PA", new BigDecimal("2"), businessDay(today.minusMonths(20)), userId);
        dividend(pea, "TTE.PA", new BigDecimal("63.20"), businessDay(today.minusMonths(14)), userId);
        dividend(pea, "AI.PA", new BigDecimal("32.00"), businessDay(today.minusMonths(17)), userId);
        dividend(pea, "AI.PA", new BigDecimal("33.00"), businessDay(today.minusMonths(5)), userId);

        // Crypto : un peu de bitcoin, une prise de bénéfices
        UUID crypto = portfolio(userId, "Crypto (démo)", PortfolioType.CRYPTO, false, null, null);
        buyQuantity(crypto, "BTC-EUR", new BigDecimal("0.02"), today.minusMonths(24), userId);
        buyQuantity(crypto, "ETH-EUR", new BigDecimal("0.4"), today.minusMonths(18), userId);
        buyQuantity(crypto, "BTC-EUR", new BigDecimal("0.01"), today.minusMonths(10), userId);
        sellQuantity(crypto, "BTC-EUR", new BigDecimal("0.008"), today.minusMonths(3), userId);

        // Livret A : épargne de précaution, intérêts crédités chaque 31 décembre
        UUID livret = portfolio(userId, "Livret A (démo)", PortfolioType.LIVRET, true, new BigDecimal("2.4"), null);
        movement(livret, CashMovementType.DEPOSIT, "6000", start.minusMonths(1), userId);
        movement(livret, CashMovementType.DEPOSIT, "1500", today.minusMonths(12), userId);
        movement(livret, CashMovementType.WITHDRAWAL, "800", today.minusMonths(7), userId);
        yearlyInterest(livret, "150", start, today, userId);

        // Assurance-vie : fonds euros + ETF + fonds maison non coté
        UUID av = portfolio(userId, "Assurance-vie (démo)", PortfolioType.ASSURANCE_VIE, true, new BigDecimal("2.5"),
                start.minusMonths(6));
        movement(av, CashMovementType.DEPOSIT, "8000", start, userId);
        movement(av, CashMovementType.DEPOSIT, "3000", today.minusMonths(14), userId);
        buyQuantity(av, "PUST.PA", new BigDecimal("30"), businessDay(start.plusMonths(1)), userId);
        AssetResponse fund = manualAssetService.create(av, new ManualAssetRequest("Fonds Patrimoine Équilibre (démo)",
                AssetType.FONDS, "EUR"), userId);
        transactionService.create(av, new TransactionCreateRequest(fund.symbol(), TransactionType.BUY, new BigDecimal("20"),
                new BigDecimal("100"), BigDecimal.ZERO, "EUR", businessDay(start.plusMonths(2)).atTime(10, 0), null), userId);
        double[] navs = { 101.8, 104.5, 103.2, 107.9, 110.4, 112.6 };
        for (int i = 0; i < navs.length; i++) {
            LocalDate d = start.plusMonths(2 + 6L * (i + 1));
            if (d.isBefore(today)) {
                manualAssetService.saveValuation(av, fund.id(), new ValuationRequest(d, BigDecimal.valueOf(navs[i])), userId);
            }
        }
        yearlyInterest(av, "180", start, today, userId);

        goalService.create(new GoalRequest("Apport pour un appartement", new BigDecimal("60000"),
                today.plusYears(6).withDayOfYear(1), null), userId);
        return pea;
    }

    /**
     * Versement programmé à venir sur le PEA. Après la validation du reste :
     * l'exécuteur de plans travaille dans sa propre transaction.
     */
    public void seedPlan(UUID peaId, UUID userId) {
        planService.create(peaId, new PlanRequest(InvestmentPlan.Type.BUY, "CW8.PA", new BigDecimal("200"), BigDecimal.ZERO,
                PlanFrequency.MONTHLY, LocalDate.now().plusMonths(1).withDayOfMonth(5), null, true, true), userId);
    }

    private UUID portfolio(UUID userId, String name, PortfolioType type, boolean cash, BigDecimal rate, LocalDate openedAt) {
        return portfolioService.create(new PortfolioCreateRequest(name, "Portefeuille fictif du mode démo", type, cash,
                rate, openedAt, null), userId).id();
    }

    /** Achat d'un montant (parts fractionnées) au cours de clôture du jour. */
    private void buyAmount(UUID pid, String symbol, BigDecimal amount, LocalDate day, UUID userId) {
        close(symbol, day).ifPresent(price -> trade(pid, symbol, TransactionType.BUY,
                amount.divide(price, 4, RoundingMode.DOWN), price, day, userId));
    }

    private void buyQuantity(UUID pid, String symbol, BigDecimal quantity, LocalDate day, UUID userId) {
        close(symbol, day).ifPresent(price -> trade(pid, symbol, TransactionType.BUY, quantity, price, day, userId));
    }

    private void sellQuantity(UUID pid, String symbol, BigDecimal quantity, LocalDate day, UUID userId) {
        close(symbol, day).ifPresent(price -> trade(pid, symbol, TransactionType.SELL, quantity, price, day, userId));
    }

    private void trade(UUID pid, String symbol, TransactionType type, BigDecimal quantity, BigDecimal price,
            LocalDate day, UUID userId) {
        if (quantity.signum() <= 0) {
            return;
        }
        transactionService.create(pid, new TransactionCreateRequest(symbol, type, quantity,
                price.setScale(4, RoundingMode.HALF_UP), type == TransactionType.BUY ? BigDecimal.ONE : new BigDecimal("0.5"),
                "EUR", day.atTime(15, 30), null), userId);
    }

    private void dividend(UUID pid, String symbol, BigDecimal amount, LocalDate day, UUID userId) {
        transactionService.create(pid, new TransactionCreateRequest(symbol, TransactionType.DIVIDEND, BigDecimal.ONE,
                amount, BigDecimal.ZERO, "EUR", day.atTime(9, 0), null), userId);
    }

    private void movement(UUID pid, CashMovementType type, String amount, LocalDate day, UUID userId) {
        cashMovementService.create(pid, new CashMovementRequest(type, new BigDecimal(amount), day, null), userId);
    }

    private void yearlyInterest(UUID pid, String amount, LocalDate start, LocalDate today, UUID userId) {
        for (int y = start.getYear(); y < today.getYear(); y++) {
            movement(pid, CashMovementType.INTEREST, amount, LocalDate.of(y, 12, 31), userId);
        }
    }

    private Optional<BigDecimal> close(String symbol, LocalDate day) {
        Optional<BigDecimal> price = priceLookup.priceInEur(symbol, day);
        if (price.isEmpty()) {
            log.warn("Démo : pas de cours pour {} le {}, opération ignorée", symbol, day);
        }
        return price;
    }

    /** Jour ouvré le plus proche (vendredi pour un week-end). */
    private static LocalDate businessDay(LocalDate d) {
        if (d.getDayOfWeek() == DayOfWeek.SATURDAY) return d.minusDays(1);
        if (d.getDayOfWeek() == DayOfWeek.SUNDAY) return d.minusDays(2);
        return d;
    }
}
