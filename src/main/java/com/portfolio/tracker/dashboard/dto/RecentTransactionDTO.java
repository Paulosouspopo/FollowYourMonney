package com.portfolio.tracker.dashboard.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecentTransactionDTO {

    private UUID transactionId;
    private String assetSymbol;
    private String assetName;
    private String type;                    // BUY, SELL, DIVIDEND
    
    private BigDecimal quantity;
    private BigDecimal pricePerUnit;
    private BigDecimal totalAmount;
    
    private String currency;
    private LocalDateTime transactionDate;
    private String notes;
}