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

    public PortfolioResponse findById(UUID id) {
        Portfolio portfolio = getPortfolioOrThrow(id);
        return portfolioMapper.toResponse(portfolio);
    }

    public List<PortfolioResponse> findByUserId(UUID userId) {
        return portfolioRepository.findByUserId(userId).stream()
                .map(portfolioMapper::toResponse)
                .toList();
    }

    @Transactional
    public PortfolioResponse create(PortfolioCreateRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", request.userId()));

        if (portfolioRepository.existsByNameAndUserId(request.name(), request.userId())) {
            throw new ResourceAlreadyExistsException(
                    "Un portefeuille nommé '" + request.name() + "' existe déjà pour cet utilisateur");
        }

        Portfolio portfolio = portfolioMapper.toEntity(request, user);
        Portfolio saved = portfolioRepository.save(portfolio);
        return portfolioMapper.toResponse(saved);
    }

    @Transactional
    public PortfolioResponse update(UUID id, PortfolioUpdateRequest request) {
        Portfolio existingPortfolio = getPortfolioOrThrow(id);

        boolean nameTaken = portfolioRepository.existsByNameAndUserIdAndIdNot(
                request.name(), existingPortfolio.getUser().getId(), id);

        if (nameTaken) {
            throw new ResourceAlreadyExistsException(
                    "Un portefeuille nommé '" + request.name() + "' existe déjà pour cet utilisateur");
        }

        existingPortfolio.setName(request.name());
        existingPortfolio.setDescription(request.description());
        existingPortfolio.setType(request.type());

        Portfolio saved = portfolioRepository.save(existingPortfolio);
        return portfolioMapper.toResponse(saved);
    }

    @Transactional
    public void deleteById(UUID id) {
        if (!portfolioRepository.existsById(id)) {
            throw new ResourceNotFoundException("Portfolio", id);
        }
        portfolioRepository.deleteById(id);
    }

    private Portfolio getPortfolioOrThrow(UUID id) {
        return portfolioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio", id));
    }
}
