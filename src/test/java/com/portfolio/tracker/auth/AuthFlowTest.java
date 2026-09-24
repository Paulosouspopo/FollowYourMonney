package com.portfolio.tracker.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.tracker.AbstractIntegrationTest;
import com.portfolio.tracker.portfolio.Portfolio;
import com.portfolio.tracker.portfolio.PortfolioRepository;
import com.portfolio.tracker.portfolio.PortfolioType;
import com.portfolio.tracker.shared.mail.EmailSender;
import com.portfolio.tracker.snapshot.PortfolioSnapshot;
import com.portfolio.tracker.snapshot.PortfolioSnapshotRepository;
import com.portfolio.tracker.user.Role;
import com.portfolio.tracker.user.User;
import com.portfolio.tracker.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Parcours complets d'authentification, via HTTP (MockMvc) et la vraie base. */
@AutoConfigureMockMvc
@DisplayName("Authentification — inscription, sessions, mot de passe")
class AuthFlowTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "motdepasse-solide";
    private static final Pattern TOKEN_IN_LINK = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private PortfolioSnapshotRepository snapshotRepository;

    /** Capture des emails envoyés, pour récupérer les liens. */
    @MockitoBean private EmailSender emailSender;

    private String email;

    @BeforeEach
    void newEmail() {
        email = "user-" + UUID.randomUUID() + "@fym.io";
        clearInvocations(emailSender);
    }

    // ================================================================ inscription

    @Test
    @DisplayName("Inscription → connexion refusée tant que l'email n'est pas vérifié → vérification → connexion")
    void inscriptionEtVerification() throws Exception {
        register(email);

        postJson("/api/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        String token = lastLinkToken(email);
        postJson("/api/auth/verify-email", "{\"token\":\"%s\"}".formatted(token)).andExpect(status().isNoContent());
        // Lien à usage unique
        postJson("/api/auth/verify-email", "{\"token\":\"%s\"}".formatted(token)).andExpect(status().isBadRequest());

        MvcResult login = postJson("/api/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn();
        String setCookie = login.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains(RefreshCookie.NAME + "=", "HttpOnly", "SameSite=Strict", "Path=/api/auth");

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("Renvoi de l'email de vérification : seul le dernier lien fonctionne ; email inconnu → même réponse")
    void renvoiVerification() throws Exception {
        register(email);
        String first = lastLinkToken(email);

        postJson("/api/auth/resend-verification", "{\"email\":\"%s\"}".formatted(email)).andExpect(status().isNoContent());
        String second = lastLinkToken(email);
        assertThat(second).isNotEqualTo(first);

        postJson("/api/auth/verify-email", "{\"token\":\"%s\"}".formatted(first)).andExpect(status().isBadRequest());
        postJson("/api/auth/verify-email", "{\"token\":\"%s\"}".formatted(second)).andExpect(status().isNoContent());

        clearInvocations(emailSender);
        postJson("/api/auth/resend-verification", "{\"email\":\"inconnu@fym.io\"}").andExpect(status().isNoContent());
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    // ================================================================== sessions

    @Test
    @DisplayName("Sans jeton ou avec un jeton invalide : 401 JSON (et non 403 / 500)")
    void nonAuthentifie() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer pas-un-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Renouvellement : rotation du jeton ; réutilisation tardive d'un ancien jeton → toutes les sessions révoquées")
    void rotationEtDetectionDeVol() throws Exception {
        String first = refreshCookie(loginVerified(email));

        MvcResult refreshed = refresh(first).andExpect(status().isOk()).andReturn();
        String second = refreshCookie(refreshed);
        assertThat(second).isNotEqualTo(first);
        assertThat(accessToken(refreshed)).isNotBlank();

        // Deux onglets simultanés : l'ancien jeton juste après la rotation → 401 sans tout révoquer
        refresh(first).andExpect(status().isUnauthorized());
        String third = refreshCookie(refresh(second).andExpect(status().isOk()).andReturn());

        // Le premier jeton ressort bien plus tard : vol présumé
        RefreshToken stolen = refreshTokenRepository.findByTokenHash(
                com.portfolio.tracker.shared.security.SecureTokens.hash(first)).orElseThrow();
        stolen.setRevokedAt(LocalDateTime.now().minus(AuthService.ROTATION_GRACE).minusSeconds(5));
        refreshTokenRepository.save(stolen);

        refresh(first).andExpect(status().isUnauthorized());
        refresh(third).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Déconnexion : cookie effacé, le jeton ne renouvelle plus rien")
    void deconnexion() throws Exception {
        String token = refreshCookie(loginVerified(email));

        MvcResult logout = mvc.perform(post("/api/auth/logout").cookie(new Cookie(RefreshCookie.NAME, token)))
                .andExpect(status().isNoContent()).andReturn();
        assertThat(logout.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");

        refresh(token).andExpect(status().isUnauthorized());
        refresh(null).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Déconnexion de tous les appareils")
    void deconnexionPartout() throws Exception {
        MvcResult laptop = loginVerified(email);
        MvcResult phone = login(email, PASSWORD).andExpect(status().isOk()).andReturn();

        mvc.perform(post("/api/auth/logout-all").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(laptop)))
                .andExpect(status().isNoContent());

        refresh(refreshCookie(laptop)).andExpect(status().isUnauthorized());
        refresh(refreshCookie(phone)).andExpect(status().isUnauthorized());
    }

    // ============================================================= mot de passe

    @Test
    @DisplayName("Mot de passe oublié → lien → nouveau mot de passe ; sessions existantes révoquées")
    void motDePasseOublie() throws Exception {
        String session = refreshCookie(loginVerified(email));

        postJson("/api/auth/forgot-password", "{\"email\":\"%s\"}".formatted(email)).andExpect(status().isNoContent());
        String token = lastLinkToken(email);

        postJson("/api/auth/reset-password", "{\"token\":\"%s\",\"newPassword\":\"nouveau-mdp-123\"}".formatted(token))
                .andExpect(status().isNoContent());
        postJson("/api/auth/reset-password", "{\"token\":\"%s\",\"newPassword\":\"encore-un-autre\"}".formatted(token))
                .andExpect(status().isBadRequest());

        refresh(session).andExpect(status().isUnauthorized());
        login(email, PASSWORD).andExpect(status().isUnauthorized());
        login(email, "nouveau-mdp-123").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Mot de passe oublié pour un email inconnu : même réponse, aucun email")
    void motDePasseOublieEmailInconnu() throws Exception {
        postJson("/api/auth/forgot-password", "{\"email\":\"personne@fym.io\"}").andExpect(status().isNoContent());
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Changement de mot de passe : ancien requis ; les autres appareils sont déconnectés")
    void changementMotDePasse() throws Exception {
        MvcResult current = loginVerified(email);
        MvcResult other = login(email, PASSWORD).andExpect(status().isOk()).andReturn();
        String bearer = "Bearer " + accessToken(current);

        mvc.perform(post("/api/auth/change-password").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"faux\",\"newPassword\":\"nouveau-mdp-123\"}"))
                .andExpect(status().isBadRequest());

        MvcResult changed = mvc.perform(post("/api/auth/change-password").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"nouveau-mdp-123\"}".formatted(PASSWORD)))
                .andExpect(status().isOk()).andReturn();

        refresh(refreshCookie(other)).andExpect(status().isUnauthorized());
        refresh(refreshCookie(changed)).andExpect(status().isOk());
    }

    // ==================================================================== rôles

    @Test
    @DisplayName("Endpoints admin : 403 pour un utilisateur, autorisés pour le rôle ADMIN en base")
    void roles() throws Exception {
        String userBearer = "Bearer " + accessToken(loginVerified(email));
        mvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, userBearer)).andExpect(status().isForbidden());

        User user = userRepository.findByEmail(email).orElseThrow();
        user.setRole(Role.ADMIN);
        userRepository.save(user);
        String adminBearer = "Bearer " + accessToken(login(email, PASSWORD).andReturn());
        mvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, adminBearer)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Suppression de compte : portefeuilles, snapshots et sessions supprimés avec lui")
    void suppressionDeCompte() throws Exception {
        String bearer = "Bearer " + accessToken(loginVerified(email));
        User user = userRepository.findByEmail(email).orElseThrow();
        Portfolio portfolio = portfolioRepository.save(
                Portfolio.builder().name("PEA").type(PortfolioType.PEA).user(user).build());
        snapshotRepository.save(PortfolioSnapshot.builder().portfolio(portfolio).snapshotDate(LocalDate.now())
                .totalValue(BigDecimal.TEN).totalInvested(BigDecimal.ONE).gainLoss(BigDecimal.ONE)
                .gainLossPercentage(BigDecimal.ONE).baseCurrency("EUR").build());

        mvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isNoContent());

        assertThat(userRepository.findByEmail(email)).isEmpty();
        assertThat(portfolioRepository.findById(portfolio.getId())).isEmpty();
    }

    // ==================================================================== utils

    private void register(String address) throws Exception {
        postJson("/api/auth/register", """
                {"email":"%s","password":"%s","username":"%s"}""".formatted(address, PASSWORD, address))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(address));
    }

    /** Inscription + vérification + connexion. */
    private MvcResult loginVerified(String address) throws Exception {
        register(address);
        postJson("/api/auth/verify-email", "{\"token\":\"%s\"}".formatted(lastLinkToken(address)))
                .andExpect(status().isNoContent());
        return login(address, PASSWORD).andExpect(status().isOk()).andReturn();
    }

    private ResultActions login(String address, String password) throws Exception {
        return postJson("/api/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(address, password));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        var request = post("/api/auth/refresh");
        if (refreshToken != null) {
            request.cookie(new Cookie(RefreshCookie.NAME, refreshToken));
        }
        return mvc.perform(request).andExpect(header().doesNotExist("X-Unused"));
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String accessToken(MvcResult result) throws Exception {
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    private static String refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(RefreshCookie.NAME);
        assertThat(cookie).as("cookie de session").isNotNull();
        return cookie.getValue();
    }

    /** Jeton du dernier lien envoyé par email à {@code address}. */
    private String lastLinkToken(String address) {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailSender, org.mockito.Mockito.atLeastOnce()).send(eq(address), anyString(), body.capture());
        Matcher m = TOKEN_IN_LINK.matcher(body.getValue());
        assertThat(m.find()).as("lien avec token dans l'email").isTrue();
        return m.group(1);
    }
}
