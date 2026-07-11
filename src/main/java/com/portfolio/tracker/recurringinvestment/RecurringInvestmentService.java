package com.portfolio.tracker.recurringinvestment;

import com.portfolio.tracker.asset.Asset;
import com.portfolio.tracker.asset.AssetRepository;
import com.portfolio.tracker.recurringinvestment.dto.RecurringInvestmentCreateRequest;
import com.portfolio.tracker.recurringinvestment.dto.RecurringInvestmentResponse;
import com.portfolio.tracker.recurringinvestment.dto.RecurringInvestmentUpdateRequest;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
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

    public RecurringInvestmentResponse findById(UUID id) {
        return recurringInvestmentMapper.toResponse(getOrThrow(id));
    }

    public List<RecurringInvestmentResponse> findByAssetId(UUID assetId) {
        if (!assetRepository.existsById(assetId)) {
            throw new ResourceNotFoundException("Asset", assetId);
        }
        return recurringInvestmentRepository.findByAssetId(assetId).stream()
                .map(recurringInvestmentMapper::toResponse)
                .toList();
    }

    @Transactional
    public RecurringInvestmentResponse create(RecurringInvestmentCreateRequest request) {
        Asset asset = assetRepository.findById(request.assetId())
                .orElseThrow(() -> new ResourceNotFoundException("Asset", request.assetId()));

        RecurringInvestment recurringInvestment = recurringInvestmentMapper.toEntity(request, asset);
        RecurringInvestment saved = recurringInvestmentRepository.save(recurringInvestment);
        return recurringInvestmentMapper.toResponse(saved);
    }

    @Transactional
    public RecurringInvestmentResponse update(UUID id, RecurringInvestmentUpdateRequest request) {
        RecurringInvestment existing = getOrThrow(id);

        existing.setAmount(request.amount());
        existing.setFrequency(request.frequency());
        existing.setEndDate(request.endDate());
        existing.setActive(request.active());

        RecurringInvestment saved = recurringInvestmentRepository.save(existing);
        return recurringInvestmentMapper.toResponse(saved);
    }

    @Transactional
    public void deleteById(UUID id) {
        if (!recurringInvestmentRepository.existsById(id)) {
            throw new ResourceNotFoundException("RecurringInvestment", id);
        }
        recurringInvestmentRepository.deleteById(id);
    }

    private RecurringInvestment getOrThrow(UUID id) {
        return recurringInvestmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RecurringInvestment", id));
    }
}