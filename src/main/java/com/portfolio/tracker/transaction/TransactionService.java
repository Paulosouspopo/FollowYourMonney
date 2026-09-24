package com.portfolio.tracker.transaction;

import com.portfolio.tracker.asset.Asset;
import com.portfolio.tracker.asset.AssetRepository;
import com.portfolio.tracker.asset.AssetType;
import com.portfolio.tracker.assetprice.AssetPriceService;
import com.portfolio.tracker.exchangerate.ExchangeRateService;
import com.portfolio.tracker.marketdata.MarketDataProvider;
import com.portfolio.tracker.marketdata.MarketQuote;
import com.portfolio.tracker.marketdata.yahoo.YahooFinanceClient;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.shared.MoneyConstants;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import com.portfolio.tracker.transaction.dto.TransactionCreateRequest;
import com.portfolio.tracker.transaction.dto.TransactionResponse;
import com.portfolio.tracker.transaction.dto.TransactionUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TransactionService {

        private final TransactionRepository transactionRepository;
        private final AssetRepository assetRepository;
        private final PortfolioRepository portfolioRepository;
        private final MarketDataProvider marketDataProvider;
        private final AssetPriceService assetPriceService;
        private final ExchangeRateService exchangeRateService;
        private final TransactionMapper transactionMapper;

        // ---------------------------------------------------------------- lectures

        public List<TransactionResponse> findByAssetSymbolAndPortfolioId(String assetSymbol,
                        UUID portfolioId,
                        UUID userId) {
                if (!assetRepository.existsBySymbolAndPortfolioIdAndUserId(assetSymbol, portfolioId, userId)) {
                        throw new ResourceNotFoundException("Asset", assetSymbol);
                }
                return transactionRepository.findByAssetSymbolAndUserId(assetSymbol, userId).stream()
                                .map(transactionMapper::toResponse)
                                .toList();
        }

        public TransactionResponse findByIdAndUserId(UUID transactionId, UUID userId) {
                Transaction transaction = transactionRepository.findByIdAndUserId(transactionId, userId)
                                .orElseThrow(() -> new ResourceNotFoundException("Transaction non accessible"));
                return transactionMapper.toResponse(transaction);
        }

        @Transactional
        public TransactionResponse create(UUID portfolioId, TransactionCreateRequest request, UUID userId) {

                validateBusinessRules(request.type(), request.quantity(), request.pricePerUnit());

                // Vérifier que le Portfolio appartient à l'utilisateur
                Portfolio portfolio = portfolioRepository.findByIdAndUserId(portfolioId, userId)
                                .orElseThrow(() -> new ResourceNotFoundException("Portfolio non accessible"));

                String symbol = request.symbol().trim().toUpperCase();

                // Chercher l'Asset dans ce portefeuille, sinon le créer après validation Yahoo
                Asset asset = assetRepository
                                .findBySymbolAndPortfolioIdAndUserId(symbol, portfolioId, userId)
                                .orElseGet(() -> createAssetFromMarket(symbol, portfolio));

                String currency = resolveCurrency(request.currency());
                BigDecimal fees = nullSafe(request.fees());

                BigDecimal totalAmount = request.quantity()
                                .multiply(request.pricePerUnit())
                                .setScale(MoneyConstants.MONEY_SCALE, MoneyConstants.ROUNDING);

                // Taux figé au moment de l'opération : c'est la vérité historique
                BigDecimal rateToEur = exchangeRateService.getRate(currency, MoneyConstants.BASE_CURRENCY);

                Transaction transaction = Transaction.builder()
                                .asset(asset)
                                .type(request.type())
                                .quantity(request.quantity())
                                .pricePerUnit(request.pricePerUnit())
                                .fees(fees)
                                .totalAmount(totalAmount)
                                .currency(currency)
                                .exchangeRateToEur(rateToEur)
                                .totalAmountEur(toEur(totalAmount, rateToEur))
                                .feesEur(toEur(fees, rateToEur))
                                .transactionDate(request.transactionDate() != null
                                                ? request.transactionDate()
                                                : LocalDateTime.now())
                                .notes(request.notes())
                                .build();

                Transaction saved = transactionRepository.save(transaction);
                log.debug("Transaction {} créée : {} {} {} @ {} {} (taux EUR {})",
                                saved.getId(), saved.getType(), saved.getQuantity(),
                                asset.getSymbol(), saved.getPricePerUnit(), currency, rateToEur);

                assetPriceService.ensurePriceHistory(asset.getSymbol(), saved.getTransactionDate());

                return transactionMapper.toResponse(saved);
        }

        private Asset createAssetFromMarket(String symbol, Portfolio portfolio) {
                MarketQuote quote = marketDataProvider.getQuote(symbol)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Symbole inconnu ou indisponible : " + symbol));

                AssetType type = YahooFinanceClient.mapAssetType(quote.instrumentType());
                if (type == null) {
                        throw new IllegalArgumentException(
                                        "Type d'actif non supporté pour " + symbol + " : " + quote.instrumentType());
                }

                Asset asset = Asset.builder()
                                .symbol(quote.symbol()) // symbole canonique renvoyé par Yahoo
                                .name(quote.longName() != null ? quote.longName() : quote.symbol())
                                .longName(quote.longName())
                                .exchangeName(quote.exchangeName())
                                .assetType(type)
                                .currency(quote.currency())
                                .portfolio(portfolio)
                                .build();
                log.debug("Création Asset {} ({}) pour le portefeuille {}", asset.getSymbol(), type, portfolio.getId());
                return assetRepository.save(asset);
        }

        // ------------------------------------------------------------ modification

        @Transactional
        public TransactionResponse update(UUID transactionId, TransactionUpdateRequest request, UUID userId) {
                Transaction existing = transactionRepository.findByIdAndUserId(transactionId, userId)
                                .orElseThrow(() -> new ResourceNotFoundException("Transaction non accessible"));

                validateBusinessRules(request.type(), request.quantity(), request.pricePerUnit());

                BigDecimal fees = nullSafe(request.fees());

                BigDecimal totalAmount = request.quantity()
                                .multiply(request.pricePerUnit())
                                .setScale(MoneyConstants.MONEY_SCALE, MoneyConstants.ROUNDING);

                existing.setType(request.type());
                existing.setQuantity(request.quantity());
                existing.setPricePerUnit(request.pricePerUnit());
                existing.setFees(fees);
                existing.setTotalAmount(totalAmount);
                existing.setTransactionDate(request.transactionDate() != null
                                ? request.transactionDate()
                                : existing.getTransactionDate());
                existing.setNotes(request.notes());

                // On réutilise le taux d'origine : la devise n'est pas modifiable,
                // l'historique reste donc fidèle au jour de l'opération.
                BigDecimal rateToEur = existing.getExchangeRateToEur();
                existing.setTotalAmountEur(toEur(totalAmount, rateToEur));
                existing.setFeesEur(toEur(fees, rateToEur));

                return transactionMapper.toResponse(transactionRepository.save(existing));
        }

        // --------------------------------------------------------------- supression

        @Transactional
        public void deleteById(UUID transactionId, UUID userId) {
                Transaction transaction = transactionRepository.findByIdAndUserId(transactionId, userId)
                                .orElseThrow(() -> new ResourceNotFoundException("Transaction non accessible"));
                transactionRepository.delete(transaction);
        }

        private void validateBusinessRules(TransactionType type, BigDecimal quantity, BigDecimal pricePerUnit) {
                if (type != TransactionType.DIVIDEND && quantity.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalArgumentException(
                                        "La quantité doit être strictement positive pour un achat ou une vente");
                }
                if (type != TransactionType.DIVIDEND && pricePerUnit.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalArgumentException(
                                        "Le prix unitaire doit être strictement positif pour un achat ou une vente");
                }
        }

        private String resolveCurrency(String requested) {
                return (requested == null || requested.isBlank())
                                ? MoneyConstants.BASE_CURRENCY
                                : requested.toUpperCase();
        }

        private BigDecimal nullSafe(BigDecimal value) {
                return value != null ? value : BigDecimal.ZERO;
        }

        private BigDecimal toEur(BigDecimal amount, BigDecimal rate) {
                return amount.multiply(rate)
                                .setScale(MoneyConstants.MONEY_SCALE, MoneyConstants.ROUNDING);
        }
}