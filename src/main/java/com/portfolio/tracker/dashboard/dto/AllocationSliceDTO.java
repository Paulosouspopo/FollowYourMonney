package com.portfolio.tracker.dashboard.dto;

import lombok.*;

import java.math.BigDecimal;

/**
 * Part de la répartition du patrimoine.
 *
 * {@code category} = nom d'un {@link com.portfolio.tracker.asset.AssetType}
 * pour les positions, {@code LIVRET} pour le solde des livrets, ou
 * {@link #CASH} pour les liquidités des autres comptes suivis.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AllocationSliceDTO {

    public static final String CASH = "LIQUIDITES";

    private String category;
    private BigDecimal value;
    private BigDecimal percentage;
}
