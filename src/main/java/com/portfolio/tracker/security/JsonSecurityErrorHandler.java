package com.portfolio.tracker.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.tracker.shared.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Erreurs levées par la chaîne de sécurité, avant les contrôleurs : même
 * format JSON que {@code GlobalExceptionHandler}.
 * <ul>
 * <li>non authentifié (jeton absent/expiré) → 401 : le front renouvelle sa session ;</li>
 * <li>authentifié mais rôle insuffisant → 403.</li>
 * </ul>
 * Par défaut Spring renverrait 403 dans les deux cas, ce qui empêche le front
 * de savoir qu'il doit renouveler son jeton.
 */
@Component
@RequiredArgsConstructor
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "Authentification requise", request);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "Accès refusé", request);
    }

    private void write(HttpServletResponse response, HttpStatus status, String message,
            HttpServletRequest request) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .build());
    }
}
