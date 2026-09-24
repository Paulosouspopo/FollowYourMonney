package com.portfolio.tracker.plan;

import com.portfolio.tracker.plan.dto.PlanResponse;
import org.springframework.stereotype.Component;

@Component
public class PlanMapper {

    public PlanResponse toResponse(InvestmentPlan p) {
        return new PlanResponse(
                p.getId(),
                p.getPortfolio().getId(),
                p.getPortfolio().getName(),
                p.getPortfolio().getType(),
                p.getType(),
                p.getSymbol(),
                p.getName(),
                p.getAmount(),
                p.getFees(),
                p.getFrequency(),
                p.getStartDate(),
                p.getEndDate(),
                p.isFractional(),
                p.isActive(),
                p.getOccurrences(),
                p.getNextExecutionDate(),
                p.getLastExecutionDate(),
                p.getLastError(),
                p.getFrequency().monthly(p.getAmount()));
    }
}
