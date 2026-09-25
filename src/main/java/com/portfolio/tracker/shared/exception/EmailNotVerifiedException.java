package com.portfolio.tracker.shared.exception;

/**
 * Identifiants corrects mais adresse email pas encore confirmée.
 * Traduite en HTTP 403 avec le code {@link #CODE} : le front propose alors
 * de renvoyer l'email de vérification.
 */
public class EmailNotVerifiedException extends RuntimeException {

    public static final String CODE = "EMAIL_NOT_VERIFIED";

    public EmailNotVerifiedException() {
        super("Confirme ton adresse email avant de te connecter : un lien t'a été envoyé.");
    }
}
