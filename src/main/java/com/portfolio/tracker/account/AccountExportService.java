package com.portfolio.tracker.account;

import com.portfolio.tracker.asset.manual.ManualAssetService;
import com.portfolio.tracker.asset.manual.ValuationResponse;
import com.portfolio.tracker.cash.CashMovementService;
import com.portfolio.tracker.cash.dto.CashMovementResponse;
import com.portfolio.tracker.goal.GoalService;
import com.portfolio.tracker.goal.dto.GoalResponse;
import com.portfolio.tracker.notification.alert.AlertRuleService;
import com.portfolio.tracker.plan.PlanService;
import com.portfolio.tracker.plan.dto.PlanResponse;
import com.portfolio.tracker.portfolio.PortfolioService;
import com.portfolio.tracker.portfolio.dto.PortfolioResponse;
import com.portfolio.tracker.transaction.TransactionService;
import com.portfolio.tracker.transaction.TransactionType;
import com.portfolio.tracker.transaction.dto.TransactionResponse;
import com.portfolio.tracker.user.UserService;
import com.portfolio.tracker.user.dto.UserResponse;
import com.portfolio.tracker.watchlist.WatchlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Export de toutes les données d'un compte (RGPD, droit à la portabilité) :
 * JSON structuré, et opérations en CSV réimportable (format générique).
 * Rien n'est omis de ce que l'utilisateur a saisi ; les cours de marché,
 * publics, n'en font pas partie.
 */
@Service
@RequiredArgsConstructor
public class AccountExportService {

    private final UserService userService;
    private final PortfolioService portfolioService;
    private final TransactionService transactionService;
    private final CashMovementService cashMovementService;
    private final ManualAssetService manualAssetService;
    private final PlanService planService;
    private final GoalService goalService;
    private final AlertRuleService alertRuleService;
    private final WatchlistService watchlistService;

    public record ManualAsset(UUID assetId, String name, List<ValuationResponse> valuations) {
    }

    public record PortfolioExport(PortfolioResponse portfolio, List<TransactionResponse> transactions,
                                  List<CashMovementResponse> cashMovements, List<PlanResponse> plans,
                                  List<ManualAsset> manualAssets) {
    }

    public record Export(String format, LocalDateTime exportedAt, UserResponse account, List<PortfolioExport> portfolios,
                         List<GoalResponse> goals, List<?> alertRules, List<?> watchlist) {
    }

    public Export export(UUID userId) {
        List<PortfolioExport> portfolios = new ArrayList<>();
        for (PortfolioResponse p : portfolioService.findByUserId(userId)) {
            List<ManualAsset> manual = manualAssetService.list(p.id(), userId).stream()
                    .map(a -> new ManualAsset(a.id(), a.name(), manualAssetService.valuations(p.id(), a.id(), userId)))
                    .toList();
            portfolios.add(new PortfolioExport(p, transactionService.findByPortfolio(p.id(), null, userId),
                    cashMovementService.findByPortfolio(p.id(), userId), planService.findByPortfolio(p.id(), userId),
                    manual));
        }
        return new Export("FollowYourMoney export v1", LocalDateTime.now(), userService.findById(userId), portfolios,
                goalService.findAll(userId), alertRuleService.findAll(userId), watchlistService.list(userId));
    }

    /**
     * Opérations au format CSV générique de l'import (« ; », virgule
     * décimale) : réimportables telles quelles dans un portefeuille.
     */
    public String transactionsCsv(UUID userId) {
        StringBuilder sb = new StringBuilder("Portefeuille;Date;Type;Symbole;Nom;Quantité;Prix unitaire;Frais;Devise;Notes\r\n");
        Map<TransactionType, String> types = Map.of(TransactionType.BUY, "Achat", TransactionType.SELL, "Vente",
                TransactionType.DIVIDEND, "Dividende");
        for (PortfolioResponse p : portfolioService.findByUserId(userId)) {
            for (TransactionResponse t : transactionService.findByPortfolio(p.id(), null, userId)) {
                sb.append(csv(p.name())).append(';')
                        .append(t.transactionDate().toLocalDate()).append(';')
                        .append(types.get(t.type())).append(';')
                        .append(csv(t.symbol())).append(';')
                        .append(csv(t.assetName())).append(';')
                        .append(num(t.quantity())).append(';')
                        .append(num(t.pricePerUnit())).append(';')
                        .append(num(t.fees())).append(';')
                        .append(t.currency()).append(';')
                        .append(csv(t.notes())).append("\r\n");
            }
        }
        return sb.toString();
    }

    private static String num(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static String csv(String v) {
        if (v == null) {
            return "";
        }
        return v.contains(";") || v.contains("\"") || v.contains("\n") ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }
}
