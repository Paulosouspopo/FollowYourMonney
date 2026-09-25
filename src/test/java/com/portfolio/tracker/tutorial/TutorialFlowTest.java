package com.portfolio.tracker.tutorial;

import com.portfolio.tracker.AbstractIntegrationTest;
import com.portfolio.tracker.shared.exception.BadRequestException;
import com.portfolio.tracker.user.User;
import com.portfolio.tracker.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Tutoriels par compte")
class TutorialFlowTest extends AbstractIntegrationTest {

    @Autowired private TutorialService service;
    @Autowired private UserRepository userRepository;

    @Test
    @DisplayName("Nouveau compte : rien de vu ; visites terminées mémorisées sans doublon ; désactivation ; tout revoir")
    void progression() {
        UUID id = userRepository.save(User.builder().email("tuto-" + UUID.randomUUID() + "@fym.io")
                .username("tuto-" + UUID.randomUUID()).password("{noop}x").emailVerified(true).build()).getId();

        assertThat(service.get(id).autoEnabled()).isTrue();
        assertThat(service.get(id).completed()).isEmpty();

        service.complete(id, "welcome");
        service.complete(id, "portfolio");
        assertThat(service.complete(id, "welcome").completed()).containsExactly("welcome", "portfolio");

        assertThat(service.setAutoEnabled(id, false).autoEnabled()).isFalse();
        TutorialService.State reset = service.reset(id);
        assertThat(reset.autoEnabled()).isTrue();
        assertThat(reset.completed()).isEmpty();

        assertThatThrownBy(() -> service.complete(id, "Pas une clé !")).isInstanceOf(BadRequestException.class);
    }
}
