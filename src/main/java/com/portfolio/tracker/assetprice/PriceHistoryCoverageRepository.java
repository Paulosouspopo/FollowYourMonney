package com.portfolio.tracker.assetprice;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceHistoryCoverageRepository extends JpaRepository<PriceHistoryCoverage, String> {
}
