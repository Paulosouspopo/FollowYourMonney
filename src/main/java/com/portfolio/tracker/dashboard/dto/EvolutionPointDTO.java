package com.portfolio.tracker.dashboard.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvolutionPointDTO {
    private LocalDate date;
    private BigDecimal totalValue;
    private BigDecimal investedAmount;
    private BigDecimal gainLoss;
    private BigDecimal gainLossPercentage;
}