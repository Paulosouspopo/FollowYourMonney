package com.portfolio.tracker.transaction;

import com.portfolio.tracker.asset.Asset;
import com.portfolio.tracker.asset.AssetRepository;
import com.portfolio.tracker.asset.external.AssetExternalService;
import com.portfolio.tracker.asset.external.AssetTemplate;
import com.portfolio.tracker.exchangerate.ExchangeRateService;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import com.portfolio.tracker.transaction.dto.TransactionCreateRequest;
import com.portfolio.tracker.transaction.dto.TransactionResponse;
import com.portfolio.tracker.transaction.dto.TransactionUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AssetRepository assetRepository;
    private final PortfolioRepository portfolioRepository;
    private final AssetExternalService assetExternalService;
    private final ExchangeRateService exchangeRateService;
    private final TransactionMapper transactionMapper;

    private Asset createAsset(AssetTemplate assetTemplate, Portfolio portfolio) {
        Asset asset = Asset.builder()
                .symbol(assetTemplate.getSymbol())
                .name(assetTemplate.getName())
                .assetType(assetTemplate.getType())
                .currency(assetTemplate.getCurrency())
                .portfolio(portfolio)
                .build();
        System.out.println("Creating new Asset: " + asset.getAssetType() + " for Portfolio: " + portfolio.getId()); // Debug
        return assetRepository.save(asset);
    }

    public List<TransactionResponse> findByAssetSymbolAndPortfolioId(String assetSymbol, UUID portfolioId,
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

        // Valider que l'asset existe dans les templates
        AssetTemplate assetTemplate = assetExternalService.getAssetData(request.symbol());

        // Chercher ou créer l'Asset pour ce Portfolio
        Asset asset = assetRepository
                .findBySymbolAndPortfolioIdAndUserId(assetTemplate.getSymbol(), portfolioId, userId)
                .orElseGet(() -> createAsset(assetTemplate, portfolio));

        String currency = request.currency() != null ? request.currency() : assetTemplate.getCurrency();
        BigDecimal fees = request.fees() != null ? request.fees() : BigDecimal.ZERO;

        BigDecimal totalAmount = request.quantity()
                .multiply(request.pricePerUnit())
                .setScale(2, RoundingMode.HALF_UP);

        // Les frais alourdissent un achat, allègent le produit net d'une vente
        BigDecimal gross = request.type() == TransactionType.SELL
                ? totalAmount.subtract(fees)
                : totalAmount.add(fees);

        String baseCurrency = portfolio.getUser().getPreferredCurrency() != null
                ? portfolio.getUser().getPreferredCurrency()
                : "EUR";

        BigDecimal exchangeRate = exchangeRateService.getRate(currency, baseCurrency);

        BigDecimal totalAmountInBaseCurrency = gross
                .multiply(exchangeRate)
                .setScale(2, RoundingMode.HALF_UP);

        Transaction transaction = Transaction.builder()
                .asset(asset)
                .type(request.type())
                .quantity(request.quantity())
                .pricePerUnit(request.pricePerUnit())
                .fees(fees)
                .totalAmount(totalAmount)
                .currency(currency)
                .exchangeRate(exchangeRate)
                .baseCurrency(baseCurrency)
                .totalAmountInBaseCurrency(totalAmountInBaseCurrency)
                .transactionDate(request.transactionDate() != null ? request.transactionDate() : LocalDateTime.now())
                .notes(request.notes())
                .build();

        Transaction saved = transactionRepository.save(transaction);
        return transactionMapper.toResponse(saved);
    }

    @Transactional
    public TransactionResponse update(UUID transactionId, TransactionUpdateRequest request, UUID userId) {
        Transaction existing = transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction non accessible"));

        validateBusinessRules(request.type(), request.quantity(), request.pricePerUnit());

        BigDecimal fees = request.fees() != null ? request.fees() : BigDecimal.ZERO;

        BigDecimal totalAmount = request.quantity()
                .multiply(request.pricePerUnit())
                .setScale(2, RoundingMode.HALF_UP);

        // Les frais alourdissent un achat, allègent le produit d'une vente
        BigDecimal gross = request.type() == TransactionType.SELL
                ? totalAmount.subtract(fees)
                : totalAmount.add(fees);

        existing.setType(request.type());
        existing.setQuantity(request.quantity());
        existing.setPricePerUnit(request.pricePerUnit());
        existing.setFees(fees);
        existing.setTotalAmount(totalAmount);
        existing.setTransactionDate(
                request.transactionDate() != null ? request.transactionDate() : existing.getTransactionDate());
        existing.setNotes(request.notes());

        // On réutilise le taux d'origine : la devise n'a pas changé, l'historique reste
        // fidèle
        existing.setTotalAmountInBaseCurrency(
                gross.multiply(existing.getExchangeRate()).setScale(2, RoundingMode.HALF_UP));

        return transactionMapper.toResponse(transactionRepository.save(existing));
    }

    private void validateBusinessRules(TransactionType type, BigDecimal quantity, BigDecimal pricePerUnit) {
        if (type != TransactionType.DIVIDEND && quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("La quantité doit être strictement positive pour un achat ou une vente");
        }
        if (type != TransactionType.DIVIDEND && pricePerUnit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Le prix unitaire doit être strictement positif pour un achat ou une vente");
        }
    }

    @Transactional
    public void deleteById(UUID transactionId, UUID userId) {
        Transaction transaction = transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction non accessible"));

        transactionRepository.delete(transaction);
    }
}
