package com.portfolio.tracker.recurringinvestment;

import com.portfolio.tracker.asset.Asset;
import com.portfolio.tracker.asset.AssetRepository;
import com.portfolio.tracker.recurringinvestment.dto.RecurringInvestmentCreateRequest;
import com.portfolio.tracker.recurringinvestment.dto.RecurringInvestmentResponse;
import com.portfolio.tracker.recurringinvestment.dto.RecurringInvestmentUpdateRequest;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import com.portfolio.tracker.shared.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecurringInvestmentService {

    private final RecurringInvestmentRepository recurringInvestmentRepository;
    private final AssetRepository assetRepository;
    private final RecurringInvestmentMapper recurringInvestmentMapper;

    public RecurringInvestmentResponse findById(UUID id, UUID userId) {
        RecurringInvestment recurringInvestment = getOrThrow(id);
        validateOwnership(recurringInvestment.getAsset().getPortfolio().getUser().getId(), userId);
        return recurringInvestmentMapper.toResponse(recurringInvestment);
    }

    public List<RecurringInvestmentResponse> findByAssetId(UUID assetId, UUID userId) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", assetId));

        validateOwnership(asset.getPortfolio().getUser().getId(), userId);

        return recurringInvestmentRepository.findByAssetId(assetId).stream()
                .map(recurringInvestmentMapper::toResponse)
                .toList();
    }

    @Transactional
    public RecurringInvestmentResponse create(RecurringInvestmentCreateRequest request, UUID userId) {
        Asset asset = assetRepository.findById(request.assetId())
                .orElseThrow(() -> new ResourceNotFoundException("Asset", request.assetId()));

        validateOwnership(asset.getPortfolio().getUser().getId(), userId);

        RecurringInvestment recurringInvestment = recurringInvestmentMapper.toEntity(request, asset);
        RecurringInvestment saved = recurringInvestmentRepository.save(recurringInvestment);
        return recurringInvestmentMapper.toResponse(saved);
    }

    @Transactional
    public RecurringInvestmentResponse update(UUID id, RecurringInvestmentUpdateRequest request, UUID userId) {
        RecurringInvestment existing = getOrThrow(id);

        validateOwnership(existing.getAsset().getPortfolio().getUser().getId(), userId);

        existing.setAmount(request.amount());
        existing.setFrequency(request.frequency());
        existing.setEndDate(request.endDate());
        existing.setActive(request.active());

        RecurringInvestment saved = recurringInvestmentRepository.save(existing);
        return recurringInvestmentMapper.toResponse(saved);
    }

    @Transactional
    public void deleteById(UUID id, UUID userId) {
        RecurringInvestment recurringInvestment = getOrThrow(id);
        validateOwnership(recurringInvestment.getAsset().getPortfolio().getUser().getId(), userId);
        recurringInvestmentRepository.deleteById(id);
    }

    private RecurringInvestment getOrThrow(UUID id) {
        return recurringInvestmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RecurringInvestment", id));
    }

    private void validateOwnership(UUID resourceUserId, UUID authenticatedUserId) {
        if (!resourceUserId.equals(authenticatedUserId)) {
            throw new UnauthorizedException("Vous n'avez pas accès à cette ressource");
        }
    }

    public List<RecurringInvestmentResponse> findAllByUser(UUID userId) {
        return recurringInvestmentRepository.findByUserId(userId).stream()
                .map(recurringInvestmentMapper::toResponse)
                .toList();
    }
}