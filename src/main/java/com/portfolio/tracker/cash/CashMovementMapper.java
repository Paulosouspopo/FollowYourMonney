package com.portfolio.tracker.cash;

import com.portfolio.tracker.cash.dto.CashMovementResponse;
import org.springframework.stereotype.Component;

@Component
public class CashMovementMapper {

    public CashMovementResponse toResponse(CashMovement m) {
        return new CashMovementResponse(
                m.getId(),
                m.getPortfolio().getId(),
                m.getType(),
                m.getAmount(),
                m.getMovementDate(),
                m.getNotes(),
                m.getCreatedAt(),
                m.getUpdatedAt());
    }
}
