package com.portfolio.tracker.transaction;

import com.portfolio.tracker.asset.Asset;
import com.portfolio.tracker.asset.AssetRepository;
import com.portfolio.tracker.asset.external.AssetExternalService;
import com.portfolio.tracker.asset.external.AssetTemplate;
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

    public List<TransactionResponse> findByAssetSymbolAndPortfolioId(String assetSymbol, UUID portfolioId, UUID userId) {
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
        // Vérifier que le Portfolio appartient à l'utilisateur
        Portfolio portfolio = portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio non accessible"));
        System.out.println("Portfolio found: " + portfolio.getId()); // Debug
        // Valider que l'asset existe dans les templates
        AssetTemplate assetTemplate = assetExternalService.getAssetData(request.symbol());
        System.out.println("AssetTemplate found: " + assetTemplate.getSymbol()); // Debug
        // Chercher ou créer l'Asset pour ce Portfolio
        Asset asset = assetRepository
                .findBySymbolAndPortfolioIdAndUserId(assetTemplate.getSymbol(), portfolioId, userId)
                .orElseGet(() -> createAsset(assetTemplate, portfolio));
        System.out.println("Asset found or created: " + asset.getId()); // Debug
        // Créer la Transaction
        BigDecimal totalAmount = request.quantity().multiply(request.pricePerUnit());

        Transaction transaction = Transaction.builder()
                .asset(asset)
                .type(request.type())
                .quantity(request.quantity())
                .pricePerUnit(request.pricePerUnit())
                .totalAmount(totalAmount)
                .currency(request.currency() != null ? request.currency() : assetTemplate.getCurrency())
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

        BigDecimal totalAmount = request.quantity().multiply(request.pricePerUnit());

        existing.setType(request.type());
        existing.setQuantity(request.quantity());
        existing.setPricePerUnit(request.pricePerUnit());
        existing.setTotalAmount(totalAmount);
        existing.setCurrency(request.currency());
        existing.setTransactionDate(request.transactionDate());
        existing.setNotes(request.notes());

        Transaction saved = transactionRepository.save(existing);
        return transactionMapper.toResponse(saved);
    }

    @Transactional
    public void deleteById(UUID transactionId, UUID userId) {
        Transaction transaction = transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction non accessible"));

        transactionRepository.delete(transaction);
    }
}
