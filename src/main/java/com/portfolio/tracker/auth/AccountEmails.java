package com.portfolio.tracker.auth;

import com.portfolio.tracker.shared.mail.EmailSender;
import com.portfolio.tracker.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/** Contenu des emails de compte. Les liens pointent vers les pages du front. */
@Component
@RequiredArgsConstructor
public class AccountEmails {

    private final EmailSender emailSender;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public void sendVerification(User user, String rawToken) {
        emailSender.send(user.getEmail(), "Confirme ton adresse email — FollowYourMoney", """
                Bonjour %s,

                Bienvenue sur FollowYourMoney ! Confirme ton adresse email pour activer ton compte :

                %s

                Ce lien est valable %d heures. Si tu n'es pas à l'origine de cette inscription, ignore cet email.
                """.formatted(user.getUsername(), link("/verify-email", rawToken),
                AccountTokenType.VERIFY_EMAIL.validity().toHours()));
    }

    public void sendPasswordReset(User user, String rawToken) {
        emailSender.send(user.getEmail(), "Réinitialise ton mot de passe — FollowYourMoney", """
                Bonjour %s,

                Tu as demandé à réinitialiser ton mot de passe. Choisis-en un nouveau ici :

                %s

                Ce lien est valable %d heure et ne fonctionne qu'une fois. Si tu n'as rien demandé, ignore cet email :
                ton mot de passe actuel reste valable.
                """.formatted(user.getUsername(), link("/reset-password", rawToken),
                AccountTokenType.RESET_PASSWORD.validity().toHours()));
    }

    private String link(String path, String rawToken) {
        return UriComponentsBuilder.fromUriString(frontendUrl).path(path)
                .queryParam("token", rawToken).build().toUriString();
    }
}
