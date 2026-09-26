package com.portfolio.tracker.auth.twofactor;

import com.portfolio.tracker.AbstractIntegrationTest;
import com.portfolio.tracker.account.AccountExportService;
import com.portfolio.tracker.auth.AuthService;
import com.portfolio.tracker.shared.exception.AuthenticationFailedException;
import com.portfolio.tracker.shared.exception.BadRequestException;
import com.portfolio.tracker.user.User;
import com.portfolio.tracker.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Double authentification, sessions actives, export des données")
class SecurityFeaturesFlowTest extends AbstractIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private TwoFactorService twoFactorService;
    @Autowired private AccountExportService exportService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final AuthService.Device PHONE = new AuthService.Device("Mozilla/5.0 (iPhone)", "10.0.0.2");

    @Test
    @DisplayName("Activation, connexion en deux étapes, rejeu refusé, code de secours à usage unique")
    void twoFactor() {
        User user = user();
        AuthService.LoginOutcome first = authService.login(user.getEmail(), "Secret-123", PHONE);
        assertThat(first.session()).isNotNull();

        TwoFactorService.Setup setup = twoFactorService.setup(user.getId());
        assertThat(setup.otpauthUri()).startsWith("otpauth://totp/FollowYourMoney").contains("secret=" + setup.secret());
        assertThatThrownBy(() -> twoFactorService.enable(user.getId(), "000000")).isInstanceOf(BadRequestException.class);
        String code = Totp.code(setup.secret(), Totp.step(Instant.now().getEpochSecond()), 6);
        List<String> recovery = twoFactorService.enable(user.getId(), code);
        assertThat(recovery).hasSize(8).allMatch(c -> c.matches("[a-z2-9]{5}-[a-z2-9]{5}"));
        assertThat(twoFactorService.status(user.getId()).recoveryCodesLeft()).isEqualTo(8);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getTotpSecret()).doesNotContain(setup.secret());

        AuthService.LoginOutcome second = authService.login(user.getEmail(), "Secret-123", PHONE);
        assertThat(second.session()).isNull();
        assertThat(second.twoFactorToken()).isNotBlank();
        // Le code déjà utilisé pour l'activation ne peut pas resservir
        assertThatThrownBy(() -> authService.verifyTwoFactor(second.twoFactorToken(), code, PHONE))
                .isInstanceOf(AuthenticationFailedException.class);
        AuthService.Session session = authService.verifyTwoFactor(second.twoFactorToken(), recovery.get(0).toUpperCase(), PHONE);
        assertThat(session.accessToken()).isNotBlank();

        String another = authService.login(user.getEmail(), "Secret-123", PHONE).twoFactorToken();
        assertThatThrownBy(() -> authService.verifyTwoFactor(another, recovery.get(0), PHONE))
                .isInstanceOf(AuthenticationFailedException.class);
        assertThat(twoFactorService.status(user.getId()).recoveryCodesLeft()).isEqualTo(7);

        twoFactorService.disable(user.getId(), "Secret-123", recovery.get(1));
        assertThat(authService.login(user.getEmail(), "Secret-123", PHONE).session()).isNotNull();
    }

    @Test
    @DisplayName("Sessions : appareil, session courante, déconnexion d'un appareil ; la rotation garde la session")
    void sessions() {
        User user = user();
        AuthService.Session phone = authService.login(user.getEmail(), "Secret-123", PHONE).session();
        authService.login(user.getEmail(), "Secret-123", new AuthService.Device("Mozilla/5.0 (Windows NT 10.0)", "10.0.0.3"));
        AuthService.Session rotated = authService.refresh(phone.refreshToken(), new AuthService.Device(null, "10.0.0.9"));

        List<AuthService.SessionInfo> sessions = authService.sessions(user.getId(), rotated.refreshToken());
        assertThat(sessions).hasSize(2);
        AuthService.SessionInfo current = sessions.stream().filter(AuthService.SessionInfo::current).findFirst().orElseThrow();
        assertThat(current.userAgent()).contains("iPhone"); // conservé à la rotation
        assertThat(current.ip()).isEqualTo("10.0.0.9");

        UUID other = sessions.stream().filter(s -> !s.current()).findFirst().orElseThrow().id();
        authService.revokeSession(user.getId(), other);
        assertThat(authService.sessions(user.getId(), rotated.refreshToken())).singleElement()
                .satisfies(s -> assertThat(s.current()).isTrue());
    }

    @Test
    @DisplayName("Export : compte et données en JSON, opérations en CSV")
    void export() {
        User user = user();
        AccountExportService.Export export = exportService.export(user.getId());
        assertThat(export.account().email()).isEqualTo(user.getEmail());
        assertThat(export.portfolios()).isEmpty();
        assertThat(exportService.transactionsCsv(user.getId())).startsWith("Portefeuille;Date;Type;Symbole");
    }

    private User user() {
        return userRepository.save(User.builder().email("sec-" + UUID.randomUUID() + "@fym.io")
                .username("sec").password(passwordEncoder.encode("Secret-123")).emailVerified(true).build());
    }
}
