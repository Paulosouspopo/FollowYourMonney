package com.portfolio.tracker.shared.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Dev sans serveur SMTP : l'email (et donc le lien de vérification ou de
 * réinitialisation) est écrit dans les logs du backend.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "false", matchIfMissing = true)
public class LogEmailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String body) {
        log.info("""

                ===== EMAIL (non envoyé : app.mail.enabled=false) =====
                À      : {}
                Objet  : {}
                {}
                =======================================================""", to, subject, body);
    }
}
