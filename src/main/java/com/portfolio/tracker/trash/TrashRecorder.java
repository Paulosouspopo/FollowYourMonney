package com.portfolio.tracker.trash;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.tracker.asset.Asset;
import com.portfolio.tracker.asset.AssetType;
import com.portfolio.tracker.cash.CashMovement;
import com.portfolio.tracker.cash.CashMovementType;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioType;
import com.portfolio.tracker.transaction.Transaction;
import com.portfolio.tracker.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Met de côté ce qui va être supprimé (dans la même transaction que la
 * suppression). Sans dépendance vers les services métier : la restauration
 * est dans {@link TrashService}.
 */
@Component
@RequiredArgsConstructor
public class TrashRecorder {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TrashItemRepository repository;
    private final ObjectMapper json;

    /** Opération, avec ce qu'il faut pour recréer son actif s'il est non coté. */
    public record TxPayload(String symbol, String assetName, AssetType assetType, String assetCurrency, boolean manual,
                            TransactionType type, BigDecimal quantity, BigDecimal pricePerUnit, BigDecimal fees,
                            String currency, LocalDateTime transactionDate, String notes, String externalRef) {
    }

    public record MovementPayload(CashMovementType type, BigDecimal amount, String currency, BigDecimal counterAmount,
                                  String counterCurrency, LocalDate movementDate, String notes, String externalRef) {
    }

    public record PortfolioPayload(String name, String description, PortfolioType type, boolean cashTracking,
                                   boolean multiCurrencyCash, BigDecimal annualInterestRate, LocalDate openedAt,
                                   List<TxPayload> transactions, List<MovementPayload> movements) {
    }

    public UUID record(UUID userId, Transaction t) {
        TxPayload p = payload(t);
        return save(userId, TrashItem.Kind.TRANSACTION, t.getAsset().getPortfolio().getId(),
                label(t.getType()) + " · " + t.getAsset().getName() + " · " + t.getTransactionDate().format(DAY), p);
    }

    public UUID record(UUID userId, CashMovement m) {
        return save(userId, TrashItem.Kind.CASH_MOVEMENT, m.getPortfolio().getId(),
                label(m.getType()) + " · " + m.getAmount().stripTrailingZeros().toPlainString() + " " + m.getCurrency()
                        + " · " + m.getMovementDate().format(DAY), payload(m));
    }

    public UUID record(UUID userId, Portfolio p, List<Transaction> txs, List<CashMovement> movements) {
        PortfolioPayload payload = new PortfolioPayload(p.getName(), p.getDescription(), p.getType(), p.isCashTracking(),
                p.isMultiCurrencyCash(), p.getAnnualInterestRate(), p.getOpenedAt(),
                txs.stream().sorted(Transaction.CHRONOLOGICAL).map(TrashRecorder::payload).toList(),
                movements.stream().sorted(CashMovement.CHRONOLOGICAL).map(TrashRecorder::payload).toList());
        int count = txs.size() + movements.size();
        return save(userId, TrashItem.Kind.PORTFOLIO, p.getId(),
                "Portefeuille « " + p.getName() + " » · " + count + " opération" + (count > 1 ? "s" : ""), payload);
    }

    private UUID save(UUID userId, TrashItem.Kind kind, UUID portfolioId, String label, Object payload) {
        try {
            return repository.save(TrashItem.builder().userId(userId).kind(kind).portfolioId(portfolioId)
                    .label(label.length() > 255 ? label.substring(0, 255) : label)
                    .payload(json.writeValueAsString(payload)).deletedAt(LocalDateTime.now()).build()).getId();
        } catch (Exception e) {
            throw new IllegalStateException("Corbeille : instantané impossible", e);
        }
    }

    private static TxPayload payload(Transaction t) {
        Asset a = t.getAsset();
        return new TxPayload(a.getSymbol(), a.getName(), a.getAssetType(), a.getCurrency(), a.isManual(), t.getType(),
                t.getQuantity(), t.getPricePerUnit(), t.getFees(), t.getCurrency(), t.getTransactionDate(), t.getNotes(),
                t.getExternalRef());
    }

    private static MovementPayload payload(CashMovement m) {
        return new MovementPayload(m.getType(), m.getAmount(), m.getCurrency(), m.getCounterAmount(),
                m.getCounterCurrency(), m.getMovementDate(), m.getNotes(), m.getExternalRef());
    }

    private static String label(TransactionType type) {
        return switch (type) {
            case BUY -> "Achat";
            case SELL -> "Vente";
            case DIVIDEND -> "Dividende";
        };
    }

    private static String label(CashMovementType type) {
        return switch (type) {
            case DEPOSIT -> "Versement";
            case WITHDRAWAL -> "Retrait";
            case INTEREST -> "Intérêts";
            case FEE -> "Frais";
            case ABONDEMENT -> "Abondement";
            case CONVERSION -> "Change";
        };
    }
}
