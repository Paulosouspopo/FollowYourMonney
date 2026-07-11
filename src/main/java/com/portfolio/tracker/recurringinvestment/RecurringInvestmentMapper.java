package com.portfolio.tracker.recurringinvestment;

import com.portfolio.tracker.asset.Asset;
import com.portfolio.tracker.recurringinvestment.dto.RecurringInvestmentCreateRequest;
import com.portfolio.tracker.recurringinvestment.dto.RecurringInvestmentResponse;
import org.springframework.stereotype.Component;

@Component
public class RecurringInvestmentMapper {

    public RecurringInvestment toEntity(RecurringInvestmentCreateRequest request, Asset asset) {
        return RecurringInvestment.builder()
                .asset(asset)
                .amount(request.amount())
                .frequency(request.frequency())
                .startDate(request.startDate())
                .nextExecution(request.startDate())
                .endDate(request.endDate())
                .active(true)
                .build();
    }

    public RecurringInvestmentResponse toResponse(RecurringInvestment recurringInvestment) {
        return new RecurringInvestmentResponse(
                recurringInvestment.getId(),
                recurringInvestment.getAsset().getId(),
                recurringInvestment.getAmount(),
                recurringInvestment.getFrequency(),
                recurringInvestment.getStartDate(),
                recurringInvestment.getNextExecution(),
                recurringInvestment.getEndDate(),
                recurringInvestment.getActive(),
                recurringInvestment.getCreatedAt(),
                recurringInvestment.getUpdatedAt()
        );
    }
}