package com.portfolio.tracker.notification.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReportSettingsRepository extends JpaRepository<ReportSettings, UUID> {

    List<ReportSettings> findByFrequencyNotAndSendHour(ReportSettings.Frequency frequency, int sendHour);
}
