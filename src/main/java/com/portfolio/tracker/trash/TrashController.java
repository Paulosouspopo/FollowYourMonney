package com.portfolio.tracker.trash;

import com.portfolio.tracker.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Corbeille : éléments supprimés depuis moins de 30 jours. */
@RestController
@RequestMapping("/api/trash")
@RequiredArgsConstructor
public class TrashController {

    /** En-tête des réponses de suppression : identifiant dans la corbeille (bouton « Annuler »). */
    public static final String TRASH_ID_HEADER = "X-Trash-Id";

    private final TrashService service;

    @GetMapping
    public List<TrashService.TrashItemResponse> list(@AuthenticationPrincipal CustomUserDetails user) {
        return service.list(user.getId());
    }

    /** @return { "portfolioId": … } : où retrouver l'élément restauré */
    @PostMapping("/{id}/restore")
    public Map<String, UUID> restore(@PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails user) {
        return Map.of("portfolioId", service.restore(id, user.getId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails user) {
        service.delete(id, user.getId());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void empty(@AuthenticationPrincipal CustomUserDetails user) {
        service.empty(user.getId());
    }
}
