package com.portfolio.tracker.imports;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportAssetMappingRepository extends JpaRepository<ImportAssetMapping, UUID> {

    List<ImportAssetMapping> findByUserId(UUID userId);

    Optional<ImportAssetMapping> findByUserIdAndReference(UUID userId, String reference);
}
