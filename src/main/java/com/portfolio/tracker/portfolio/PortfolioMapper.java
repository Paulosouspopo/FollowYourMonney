package com.portfolio.tracker.portfolio;

import com.portfolio.tracker.asset.AssetMapper;
import com.portfolio.tracker.asset.dto.AssetResponse;
import com.portfolio.tracker.portfolio.dto.PortfolioCreateRequest;
import com.portfolio.tracker.portfolio.dto.PortfolioDetailResponse;
import com.portfolio.tracker.portfolio.dto.PortfolioResponse;
import com.portfolio.tracker.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PortfolioMapper {

    private final AssetMapper assetMapper;

    public Portfolio toEntity(PortfolioCreateRequest request, User user) {
        return Portfolio.builder()
                .name(request.name())
                .description(request.description())
                .type(request.type())
                .cashTracking(PortfolioRules.cashTracking(request.type(), request.cashTracking()))
                .multiCurrencyCash(PortfolioRules.cashTracking(request.type(), request.cashTracking())
                        && Boolean.TRUE.equals(request.multiCurrencyCash()))
                .annualInterestRate(request.annualInterestRate())
                .openedAt(request.openedAt())
                .user(user)
                .build();
    }

    public PortfolioResponse toResponse(Portfolio portfolio) {
        return new PortfolioResponse(
                portfolio.getId(),
                portfolio.getName(),
                portfolio.getDescription(),
                portfolio.getType(),
                portfolio.isCashTracking(),
                portfolio.isMultiCurrencyCash(),
                portfolio.getAnnualInterestRate(),
                portfolio.getOpenedAt(),
                portfolio.getUser().getId(),
                portfolio.getCreatedAt(),
                portfolio.getUpdatedAt());
    }

    public PortfolioDetailResponse toDetailResponse(Portfolio portfolio) {
        List<AssetResponse> assets = portfolio.getAssets() == null
                ? List.of()
                : portfolio.getAssets().stream()
                        .map(assetMapper::toResponse)
                        .toList();

        return new PortfolioDetailResponse(
                portfolio.getId(),
                portfolio.getName(),
                portfolio.getDescription(),
                portfolio.getType(),
                portfolio.isCashTracking(),
                portfolio.isMultiCurrencyCash(),
                portfolio.getAnnualInterestRate(),
                portfolio.getUser().getId(),
                assets,
                portfolio.getCreatedAt(),
                portfolio.getUpdatedAt());
    }
}
