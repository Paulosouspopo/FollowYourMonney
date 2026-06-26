package com.portfolio.tracker.service;

import com.portfolio.tracker.entity.Portfolio;
import com.portfolio.tracker.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;

    public Portfolio findById(UUID id) {
        return portfolioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Portfolio not found with id: " + id));
    }

    public List<Portfolio> findByUserId(UUID userId) {
        return portfolioRepository.findByUserId(userId);
    }

    public Portfolio save(Portfolio portfolio) {
        if (portfolioRepository.existsByNameAndUserId(portfolio.getName(), portfolio.getUser().getId())) {
            throw new RuntimeException("Portfolio with this name already exists for this user");
        }
        return portfolioRepository.save(portfolio);
    }

    public Portfolio update(Portfolio portfolio) {
        return portfolioRepository.save(portfolio);
    }

    public void deleteById(UUID id) {
        portfolioRepository.deleteById(id);
    }
}
