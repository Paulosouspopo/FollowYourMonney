package com.portfolio.tracker.portfolio;

import com.portfolio.tracker.asset.AssetMapper;
import com.portfolio.tracker.portfolio.dto.PortfolioCreateRequest;
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
                .user(user)
                .build();
    }

    public PortfolioResponse toResponse(Portfolio portfolio) {
        List<com.portfolio.tracker.asset.dto.AssetResponse> assets = portfolio.getAssets() == null
                ? List.of()
                : portfolio.getAssets().stream()
                    .map(assetMapper::toResponse)
                    .toList();

        return new PortfolioResponse(
                portfolio.getId(),
                portfolio.getName(),
                portfolio.getDescription(),
                portfolio.getType(),
                portfolio.getUser().getId(),
                assets,
                portfolio.getCreatedAt(),
                portfolio.getUpdatedAt()
        );
    }
}
