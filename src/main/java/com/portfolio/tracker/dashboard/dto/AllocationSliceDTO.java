package com.portfolio.tracker.dashboard.dto;

import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AllocationSliceDTO {

    private String assetType;           // AssetType (ACTION, ETF, CRYPTO, etc.)
    private BigDecimal value;           // Valeur actuelle
    private BigDecimal percentage;      // Pourcentage du portefeuille total
    private int count;                  // Nombre d'actifs de ce type
}