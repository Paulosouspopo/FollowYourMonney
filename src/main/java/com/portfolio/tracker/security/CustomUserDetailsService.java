package com.portfolio.tracker.security;

import org.springframework.beans.factory.annotation.Value;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.portfolio.tracker.user.User;
import com.portfolio.tracker.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Pas de rôle stocké en base pour l'instant : les administrateurs sont les
 * emails listés dans {@code app.admin.emails} (séparés par des virgules).
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final Set<String> adminEmails;

    public CustomUserDetailsService(UserRepository userRepository,
            @Value("${app.admin.emails:}") List<String> adminEmails) {
        this.userRepository = userRepository;
        this.adminEmails = adminEmails.stream()
                .map(e -> e.trim().toLowerCase())
                .filter(e -> !e.isEmpty())
                .collect(Collectors.toSet());
    }

    @Override
    public CustomUserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé : " + email));

        return new CustomUserDetails(user, adminEmails.contains(user.getEmail().toLowerCase()));
    }
}
