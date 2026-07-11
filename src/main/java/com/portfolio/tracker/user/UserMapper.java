package com.portfolio.tracker.user;

import com.portfolio.tracker.user.dto.UserCreateRequest;
import com.portfolio.tracker.user.dto.UserResponse;
import com.portfolio.tracker.user.dto.UserUpdateRequest;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public User toEntity(UserCreateRequest request, String hashedPassword) {
        return User.builder()
                .email(request.email())
                .password(hashedPassword)
                .username(request.username())
                .preferredCurrency(
                        request.preferredCurrency() != null ? request.preferredCurrency() : "EUR"
                )
                .build();
    }

    public void updateEntity(User user, UserUpdateRequest request) {
        user.setUsername(request.username());
        if (request.preferredCurrency() != null) {
            user.setPreferredCurrency(request.preferredCurrency());
        }
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getPreferredCurrency(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}