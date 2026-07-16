package com.portfolio.tracker.portfolio;

import com.portfolio.tracker.portfolio.dto.PortfolioCreateRequest;
import com.portfolio.tracker.portfolio.dto.PortfolioResponse;
import com.portfolio.tracker.portfolio.dto.PortfolioUpdateRequest;
import com.portfolio.tracker.shared.exception.ResourceAlreadyExistsException;
import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import com.portfolio.tracker.user.User;
import com.portfolio.tracker.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final UserRepository userRepository;
    private final PortfolioMapper portfolioMapper;

    public List<PortfolioResponse> findByUserId(UUID userId) {
        return portfolioRepository.findByUserId(userId).stream()
                .map(portfolioMapper::toResponse)
                .toList();
    }

    public PortfolioResponse findByIdAndUserId(UUID portfolioId, UUID userId) {
        Portfolio portfolio = portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio non accessible"));
        return portfolioMapper.toResponse(portfolio);
    }

    @Transactional
    public PortfolioResponse create(PortfolioCreateRequest request, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId));

        if (portfolioRepository.existsByNameAndUserId(request.name(), userId)) {
            throw new ResourceAlreadyExistsException(
                    "Un portefeuille nommé '" + request.name() + "' existe déjà pour cet utilisateur");
        }

        Portfolio portfolio = portfolioMapper.toEntity(request, user);
        Portfolio saved = portfolioRepository.save(portfolio);
        return portfolioMapper.toResponse(saved);
    }

    @Transactional
    public PortfolioResponse update(UUID portfolioId, PortfolioUpdateRequest request, UUID userId) {
        Portfolio portfolio = portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio non accessible"));

        boolean nameTaken = portfolioRepository.existsByNameAndUserIdAndIdNot(
                request.name(), userId, portfolioId);

        if (nameTaken) {
            throw new ResourceAlreadyExistsException(
                    "Un portefeuille nommé '" + request.name() + "' existe déjà pour cet utilisateur");
        }

        portfolio.setName(request.name());
        portfolio.setDescription(request.description());
        portfolio.setType(request.type());

        Portfolio saved = portfolioRepository.save(portfolio);
        return portfolioMapper.toResponse(saved);
    }

    @Transactional
    public void deleteById(UUID portfolioId, UUID userId) {
        Portfolio portfolio = portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio non accessible"));

        portfolioRepository.delete(portfolio);
    }
}