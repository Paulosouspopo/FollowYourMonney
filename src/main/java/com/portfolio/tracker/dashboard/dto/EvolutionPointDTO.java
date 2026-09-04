package com.portfolio.tracker.dashboard.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvolutionPointDTO {

    private LocalDateTime date;
    private BigDecimal portfolioValue;      // Valeur totale à cette date
    private BigDecimal gainLoss;            // Gain/perte cumulé à cette date
}