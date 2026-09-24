package com.portfolio.tracker.imports;

import com.portfolio.tracker.assetprice.MarketPriceLookup;
import com.portfolio.tracker.cash.CashMovement;
import com.portfolio.tracker.cash.CashMovementRepository;
import com.portfolio.tracker.cash.CashMovementService;
import com.portfolio.tracker.cash.dto.CashMovementRequest;
import com.portfolio.tracker.imports.csv.CsvReader;
import com.portfolio.tracker.imports.csv.CsvTable;
import com.portfolio.tracker.imports.dto.*;
import com.portfolio.tracker.imports.parser.GenericParser;
import com.portfolio.tracker.imports.parser.StatementParser;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.portfolio.PortfolioRules;
import com.portfolio.tracker.shared.MoneyConstants;
import com.portfolio.tracker.shared.exception.BadRequestException;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import com.portfolio.tracker.snapshot.PortfolioHistoryChangedEvent;
import com.portfolio.tracker.transaction.Transaction;
import com.portfolio.tracker.transaction.TransactionRepository;
import com.portfolio.tracker.transaction.TransactionService;
import com.portfolio.tracker.transaction.dto.TransactionCreateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Import de relevés en deux temps :
 * <ol>
 * <li>{@link #preview} : lecture, interprétation, proposition de symboles,
 * estimation des conversions crypto, détection des doublons. Rien n'est
 * enregistré ;</li>
 * <li>{@link #commit} : l'utilisateur a validé les actifs et coché les lignes ;
 * tout est créé via les services habituels (mêmes règles qu'une saisie), dans
 * une seule transaction : une ligne en erreur annule tout l'import.</li>
 * </ol>
 * L'historique est recalculé une seule fois à la fin (les événements d'une
 * même transaction sont regroupés par {@code PortfolioHistoryListener}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportService {

    private static final int SAMPLE_ROWS = 5;
    private static final int MAX_DISTINCT_VALUES = 20;
    /** Référence des transactions générées par un investissement programmé. */
    private static final String PLAN_REF_PREFIX = "PLAN:";
    /** Un courtier exécute une échéance jusqu'à quelques jours après la date prévue. */
    private static final int PLAN_MATCH_DAYS = 4;

    private final List<StatementParser> parsers;
    private final GenericParser genericParser;
    private final AssetResolver assetResolver;
    private final ImportAssetMappingRepository mappingRepository;
    private final PortfolioRepository portfolioRepository;
    private final TransactionRepository transactionRepository;
    private final CashMovementRepository cashMovementRepository;
    private final TransactionService transactionService;
    private final CashMovementService cashMovementService;
    private final MarketPriceLookup marketPriceLookup;
    private final ApplicationEventPublisher eventPublisher;

    // ================================================================= lecture

    public ImportInspection inspect(byte[] file) {
        CsvTable table = CsvReader.read(file);
        return new ImportInspection(
                detect(table).map(StatementParser::format).orElse(null),
                table.encoding(),
                table.delimiter() == '\t' ? "tabulation" : String.valueOf(table.delimiter()),
                table.headers(),
                table.lines().stream().limit(SAMPLE_ROWS).map(CsvTable.Line::cells).toList(),
                table.lines().size(),
                columnValues(table));
    }

    /** Pour associer les valeurs d'une colonne « type » (Achat, Vente...) aux types de l'application. */
    private static Map<String, List<String>> columnValues(CsvTable table) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (int i = 0; i < table.headers().size(); i++) {
            int column = i;
            Set<String> values = new LinkedHashSet<>();
            for (CsvTable.Line line : table.lines()) {
                String v = line.cell(column);
                if (!v.isEmpty()) {
                    values.add(v);
                }
                if (values.size() > MAX_DISTINCT_VALUES) {
                    break;
                }
            }
            if (!values.isEmpty() && values.size() <= MAX_DISTINCT_VALUES) {
                result.put(table.headers().get(i), new ArrayList<>(values));
            }
        }
        return result;
    }

    /**
     * Pas de transaction englobante : l'aperçu fait des appels réseau (Yahoo)
     * et chaque lecture base a sa propre transaction courte.
     */
    public ImportPreview preview(UUID userId, PreviewOptions options, byte[] file) {
        Portfolio portfolio = ownedPortfolio(options.portfolioId(), userId);
        CsvTable table = CsvReader.read(file);

        ImportFormat format;
        List<ImportedOperation> operations;
        if (options.format() == ImportFormat.GENERIC) {
            if (options.mapping() == null) {
                throw new BadRequestException("Associe les colonnes du fichier pour l'import générique");
            }
            format = ImportFormat.GENERIC;
            operations = genericParser.parse(table, options.mapping());
        } else {
            StatementParser parser = options.format() != null
                    ? parsers.stream().filter(p -> p.format() == options.format()).findFirst().orElseThrow()
                    : detect(table).orElseThrow(() -> new BadRequestException(
                            "Format de fichier non reconnu : associe les colonnes manuellement"));
            if (!parser.supports(table)) {
                throw new BadRequestException("Ce fichier ne ressemble pas à un relevé " + parser.format().label());
            }
            format = parser.format();
            operations = parser.parse(table);
        }

        if (PortfolioRules.holdsOnlyCash(portfolio.getType())) {
            operations.stream()
                    .filter(op -> op.getStatus() == RowStatus.READY && op.getKind().isTrade())
                    .forEach(op -> fail(op, "Un livret ne détient pas d'actifs"));
        }

        Map<String, AssetResolutionDto> assets = resolveAssets(userId, operations);
        estimatePrices(operations, assets);
        markDuplicates(operations, portfolio, userId, assets);

        List<ImportRowDto> rows = IntStream.range(0, operations.size())
                .mapToObj(i -> toRow(i, operations.get(i)))
                .toList();
        return new ImportPreview(format, format.label(), portfolio.isCashTracking(), rows,
                new ArrayList<>(assets.values()));
    }

    // =============================================================== validation

    @Transactional
    public ImportCommitResult commit(UUID userId, ImportCommitRequest request) {
        Portfolio portfolio = ownedPortfolio(request.portfolioId(), userId);
        Map<String, String> assets = request.assets() == null ? Map.of() : request.assets();
        List<ImportRowDto> rows = request.rows().stream()
                .sorted(Comparator.comparing(ImportRowDto::dateTime).thenComparing(ImportRowDto::id))
                .toList();

        boolean hasCash = rows.stream().anyMatch(r -> !r.kind().isTrade());
        if (hasCash && !portfolio.isCashTracking()) {
            if (!request.enableCashTracking()) {
                throw new BadRequestException(
                        "Le fichier contient des versements/retraits : active le suivi des liquidités pour les importer");
            }
            portfolio.setCashTracking(true);
            eventPublisher.publishEvent(PortfolioHistoryChangedEvent.full(portfolio.getId()));
        }
        rememberMappings(userId, rows, assets);

        Set<String> existingRefs = existingExternalRefs(portfolio.getId(), userId);
        int transactions = 0, movements = 0, skipped = 0;
        for (ImportRowDto row : rows) {
            if (row.externalRef() != null && existingRefs.contains(row.externalRef())) {
                skipped++; // déjà importée (double validation, réimport du même relevé)
                continue;
            }
            try {
                if (row.kind().isTrade()) {
                    importTrade(portfolio, row, assets, userId);
                    transactions++;
                } else {
                    cashMovementService.create(portfolio.getId(), new CashMovementRequest(
                            row.kind().toCashMovementType(), money(row.amount()), row.dateTime().toLocalDate(),
                            row.notes()), userId, row.externalRef());
                    movements++;
                }
            } catch (ImportRowException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ImportRowException(row.id(), row.lines(), e.getMessage());
            }
            if (row.externalRef() != null) {
                existingRefs.add(row.externalRef());
            }
        }
        log.info("Import dans le portefeuille {} : {} opération(s), {} mouvement(s), {} déjà présente(s)",
                portfolio.getId(), transactions, movements, skipped);
        return new ImportCommitResult(transactions, movements, skipped);
    }

    private void importTrade(Portfolio portfolio, ImportRowDto row, Map<String, String> assets, UUID userId) {
        String symbol = assets.get(row.assetReference());
        if (symbol == null || symbol.isBlank()) {
            throw new ImportRowException(row.id(), row.lines(), "choisis l'actif correspondant à « " + row.assetLabel() + " »");
        }
        BigDecimal unitPrice = row.unitPrice();
        String currency = row.currency();
        if (row.priceEstimated()) {
            String valuationSymbol = assets.get(row.valuationReference());
            unitPrice = estimateUnitPrice(valuationSymbol, row.valuationQuantity(), row.quantity(), row.dateTime().toLocalDate())
                    .orElseThrow(() -> new ImportRowException(row.id(), row.lines(),
                            "valeur en euros introuvable pour cette conversion"));
            currency = MoneyConstants.BASE_CURRENCY;
        }
        if (unitPrice == null) {
            throw new ImportRowException(row.id(), row.lines(), "prix manquant");
        }
        transactionService.create(portfolio.getId(), new TransactionCreateRequest(
                symbol,
                row.kind().toTransactionType(),
                row.quantity().setScale(8, RoundingMode.HALF_UP),
                unitPrice.setScale(8, RoundingMode.HALF_UP),
                money(row.fees()),
                currency,
                row.dateTime(),
                row.notes()), userId, row.externalRef());
    }

    // ================================================================ étapes

    private Optional<StatementParser> detect(CsvTable table) {
        return parsers.stream().filter(p -> p.supports(table)).findFirst();
    }

    /** Un appel Yahoo au plus par actif distinct du relevé. */
    private Map<String, AssetResolutionDto> resolveAssets(UUID userId, List<ImportedOperation> operations) {
        Map<String, String> remembered = mappingRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(ImportAssetMapping::getReference, ImportAssetMapping::getSymbol, (a, b) -> b));
        Map<String, AssetRef> refs = new LinkedHashMap<>();
        for (ImportedOperation op : operations) {
            if (op.getStatus() == RowStatus.IGNORED) {
                continue;
            }
            if (op.getAsset() != null) {
                refs.putIfAbsent(op.getAsset().reference(), op.getAsset());
            }
            if (op.getValuationAsset() != null) {
                refs.putIfAbsent(op.getValuationAsset().reference(), op.getValuationAsset());
            }
        }
        Map<String, AssetResolutionDto> result = new LinkedHashMap<>();
        refs.forEach((reference, ref) -> result.put(reference, assetResolver.resolve(ref, remembered)));
        return result;
    }

    /** Conversions crypto → crypto : prix estimé avec le symbole proposé (recalculé à la validation). */
    private void estimatePrices(List<ImportedOperation> operations, Map<String, AssetResolutionDto> assets) {
        for (ImportedOperation op : operations) {
            if (!op.isPriceEstimated() || op.getStatus() != RowStatus.READY) {
                continue;
            }
            AssetResolutionDto valuation = assets.get(op.getValuationAsset().reference());
            String symbol = valuation == null || valuation.suggestion() == null ? null : valuation.suggestion().symbol();
            estimateUnitPrice(symbol, op.getValuationQuantity(), op.getQuantity(), op.getDateTime().toLocalDate())
                    .ifPresentOrElse(price -> {
                        op.setUnitPrice(price);
                        op.setCurrency(MoneyConstants.BASE_CURRENCY);
                    }, () -> op.setMessage("Valeur en euros estimée à la validation, d'après l'actif choisi pour "
                            + op.getValuationAsset().label()));
        }
    }

    /**
     * Prix unitaire en EUR de {@code quantity} unités valant
     * {@code valuationQuantity} × cours de clôture de {@code valuationSymbol}.
     */
    private Optional<BigDecimal> estimateUnitPrice(String valuationSymbol, BigDecimal valuationQuantity,
                                                   BigDecimal quantity, LocalDate day) {
        if (valuationQuantity == null || quantity == null || quantity.signum() == 0) {
            return Optional.empty();
        }
        return marketPriceLookup.priceInEur(valuationSymbol, day)
                .map(price -> valuationQuantity.multiply(price).divide(quantity, 8, RoundingMode.HALF_UP));
    }

    /**
     * Doublons : même référence de relevé (réimport), ou opération identique
     * déjà présente (même jour, même type, même actif, même quantité ; pour un
     * mouvement : même jour, même type, même montant). Décochés par défaut.
     */
    private void markDuplicates(List<ImportedOperation> operations, Portfolio portfolio, UUID userId,
                                Map<String, AssetResolutionDto> assets) {
        List<Transaction> existingTxs = transactionRepository.findByPortfolioIdAndUserId(portfolio.getId(), userId);
        List<CashMovement> existingMovements = cashMovementRepository.findByPortfolioIdAndUserId(portfolio.getId(), userId);
        Set<String> refs = new HashSet<>();
        Set<String> keys = new HashSet<>();
        // Achats générés par un investissement programmé : symbole|jour
        Set<String> planBuys = new HashSet<>();
        existingTxs.forEach(t -> {
            if (t.getExternalRef() != null && t.getExternalRef().startsWith(PLAN_REF_PREFIX)) {
                planBuys.add(t.getAsset().getSymbol().toUpperCase() + "|" + t.getTransactionDate().toLocalDate());
            }
            if (t.getExternalRef() != null) {
                refs.add(t.getExternalRef());
            }
            keys.add(tradeKey(t.getTransactionDate().toLocalDate(), t.getType().name(), t.getAsset().getSymbol(),
                    t.getQuantity()));
        });
        existingMovements.forEach(m -> {
            if (m.getExternalRef() != null) {
                refs.add(m.getExternalRef());
            }
            keys.add(cashKey(m.getMovementDate(), m.getType().name(), m.getAmount()));
        });

        for (ImportedOperation op : operations) {
            if (op.getStatus() != RowStatus.READY) {
                continue;
            }
            if (op.getExternalRef() != null && refs.contains(op.getExternalRef())) {
                op.setStatus(RowStatus.DUPLICATE);
                op.setMessage("Déjà importée");
                continue;
            }
            String key = op.getKind().isTrade()
                    ? Optional.ofNullable(assets.get(op.getAsset().reference()))
                            .map(AssetResolutionDto::suggestion)
                            .map(s -> tradeKey(op.getDateTime().toLocalDate(), op.getKind().name(), s.symbol(), op.getQuantity()))
                            .orElse(null)
                    : cashKey(op.getDateTime().toLocalDate(), op.getKind().name(), op.getAmount());
            if (key != null && keys.contains(key)) {
                op.setStatus(RowStatus.DUPLICATE);
                op.setMessage("Une opération identique existe déjà (même jour, même montant)");
                continue;
            }
            // Le relevé contient l'exécution réelle d'un achat déjà créé (prix estimé) par un plan
            if (op.getKind() == ImportKind.BUY) {
                String symbol = Optional.ofNullable(assets.get(op.getAsset().reference()))
                        .map(AssetResolutionDto::suggestion).map(sug -> sug.symbol().toUpperCase()).orElse(null);
                LocalDate day = op.getDateTime().toLocalDate();
                boolean fromPlan = symbol != null && IntStream.rangeClosed(-PLAN_MATCH_DAYS, PLAN_MATCH_DAYS)
                        .anyMatch(d -> planBuys.contains(symbol + "|" + day.plusDays(d)));
                if (fromPlan) {
                    op.setStatus(RowStatus.DUPLICATE);
                    op.setMessage("Déjà créé par ton investissement programmé (prix estimé) : coche pour l'importer quand même");
                }
            }
        }
    }

    private static String tradeKey(LocalDate day, String type, String symbol, BigDecimal quantity) {
        return day + "|" + type + "|" + symbol.toUpperCase() + "|" + quantity.stripTrailingZeros().toPlainString();
    }

    private static String cashKey(LocalDate day, String type, BigDecimal amount) {
        return day + "|" + type + "|" + (amount == null ? "" : amount.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    private Set<String> existingExternalRefs(UUID portfolioId, UUID userId) {
        Set<String> refs = new HashSet<>();
        transactionRepository.findByPortfolioIdAndUserId(portfolioId, userId).stream()
                .map(Transaction::getExternalRef).filter(Objects::nonNull).forEach(refs::add);
        cashMovementRepository.findByPortfolioIdAndUserId(portfolioId, userId).stream()
                .map(CashMovement::getExternalRef).filter(Objects::nonNull).forEach(refs::add);
        return refs;
    }

    /** Mémorise les associations validées : le prochain import de ce courtier se résout seul. */
    private void rememberMappings(UUID userId, List<ImportRowDto> rows, Map<String, String> assets) {
        Set<String> used = new HashSet<>();
        rows.stream().filter(r -> r.kind().isTrade()).forEach(r -> {
            used.add(r.assetReference());
            if (r.valuationReference() != null) {
                used.add(r.valuationReference());
            }
        });
        for (String reference : used) {
            String symbol = assets.get(reference);
            if (reference == null || symbol == null || symbol.isBlank()) {
                continue;
            }
            ImportAssetMapping mapping = mappingRepository.findByUserIdAndReference(userId, reference)
                    .orElseGet(() -> ImportAssetMapping.builder().userId(userId).reference(reference).build());
            mapping.setSymbol(symbol);
            mappingRepository.save(mapping);
        }
    }

    // ================================================================= utils

    private Portfolio ownedPortfolio(UUID portfolioId, UUID userId) {
        return portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio non accessible"));
    }

    private static void fail(ImportedOperation op, String reason) {
        op.setStatus(RowStatus.ERROR);
        op.setMessage(reason);
    }

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(MoneyConstants.MONEY_SCALE, MoneyConstants.ROUNDING);
    }

    private static ImportRowDto toRow(int id, ImportedOperation op) {
        return new ImportRowDto(
                id,
                op.getLines(),
                op.getDateTime(),
                op.getKind(),
                op.getAsset() == null ? null : op.getAsset().reference(),
                op.getAsset() == null ? null : op.getAsset().label(),
                op.getQuantity(),
                op.getUnitPrice(),
                op.getFees(),
                op.getCurrency(),
                op.getAmount(),
                op.getNotes(),
                op.getExternalRef(),
                op.getStatus(),
                op.getMessage(),
                op.isPriceEstimated(),
                op.getValuationAsset() == null ? null : op.getValuationAsset().reference(),
                op.getValuationQuantity());
    }
}
