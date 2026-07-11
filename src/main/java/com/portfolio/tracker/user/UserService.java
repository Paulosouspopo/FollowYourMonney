package com.portfolio.tracker.user;

import com.portfolio.tracker.shared.exception.ResourceNotFoundException;
import com.portfolio.tracker.user.dto.UserCreateRequest;
import com.portfolio.tracker.user.dto.UserResponse;
import com.portfolio.tracker.user.dto.UserUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public List<UserResponse> findAll() {
        return userRepository.findAll()
                .stream()
                .map(userMapper::toResponse)
                .toList();
    }

    public UserResponse findById(UUID id) {
        return userMapper.toResponse(getUserOrThrow(id));
    }

    public UserResponse findByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur avec l'email " + email));
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Un utilisateur avec cet email existe déjà");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Ce nom d'utilisateur est déjà pris");
        }

        String hashedPassword = passwordEncoder.encode(request.password());
        User user = userMapper.toEntity(request, hashedPassword);
        User saved = userRepository.save(user);
        return userMapper.toResponse(saved);
    }

    @Transactional
    public UserResponse update(UUID id, UserUpdateRequest request) {
        User user = getUserOrThrow(id);
        userMapper.updateEntity(user, request);
        User saved = userRepository.save(user);
        return userMapper.toResponse(saved);
    }

    @Transactional
    public void deleteById(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("Utilisateur avec l'ID " + id);
        }
        userRepository.deleteById(id);
    }

    private User getUserOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur avec l'ID " + id));
    }
}
