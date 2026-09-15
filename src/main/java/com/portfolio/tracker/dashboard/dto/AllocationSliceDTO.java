package com.portfolio.tracker.dashboard.dto;

import lombok.*;
import java.math.BigDecimal;

import com.portfolio.tracker.asset.AssetType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AllocationSliceDTO {
    private AssetType assetType;
    private String label;
    private BigDecimal value;
    private BigDecimal percentage;
}