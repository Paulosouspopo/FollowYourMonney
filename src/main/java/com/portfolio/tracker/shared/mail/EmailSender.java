package com.portfolio.tracker.shared.mail;

/**
 * Envoi d'un email texte. Deux implémentations, choisies par
 * {@code app.mail.enabled} : SMTP réel ({@link SmtpEmailSender}) ou simple
 * écriture dans les logs ({@link LogEmailSender}) en dev sans serveur mail.
 */
public interface EmailSender {

    void send(String to, String subject, String body);
}
