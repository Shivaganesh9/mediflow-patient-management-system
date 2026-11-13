package com.mediflow.auth.controller;

import com.jayway.jsonpath.JsonPath;
import com.mediflow.auth.entity.Role;
import com.mediflow.auth.entity.User;
import com.mediflow.auth.repository.RoleRepository;
import com.mediflow.auth.repository.UserRepository;
import com.mediflow.auth.security.JwtProvider;
import com.mediflow.common.enums.AccountStatus;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "auth.password-reset.expose-token=true",
        // Reuse detection must be immediate in this suite; retry tolerance is covered separately.
        "auth.refresh.reuse-grace=0s"
})
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    private static final String PASSWORD = "Test@12345";
    private static final String NEW_PASSWORD = "Changed#98765";
    private static final String AUTHORIZATION = "Authorization";
    private static final String SECRET = "test-secret-key-for-auth-service-with-sufficient-length";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    void registerEndpointShouldAcceptValidPayload() throws Exception {
        String payload = "{\"username\":\"testuser\",\"email\":\"test@example.com\",\"password\":\"password123\"}";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.expiresIn").isNumber());
    }

    @Test
    void registerEndpointShouldRejectWeakPassword() throws Exception {
        String payload = "{\"username\":\"weakuser\",\"email\":\"weak@example.com\",\"password\":\"onlyletters\"}";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void currentUserEndpointShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerThenLoginShouldReturnTokens() throws Exception {
        String username = registerUser();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(loginPayload(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        assertNotNull(JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken"));
    }

    @Test
    void consecutiveLoginsShouldReturnDistinctAccessTokens() throws Exception {
        String username = registerUser();
        String[] firstLogin = login(username);
        String[] secondLogin = login(username);

        assertNotEquals(firstLogin[0], secondLogin[0]);
        assertNotEquals(firstLogin[1], secondLogin[1]);
    }

    @Test
    void loginWithWrongPasswordReturnsGenericUnauthorizedMessage() throws Exception {
        String username = registerUser();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload(username, "WrongPass123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void repeatedFailuresTemporarilyLockTheAccountThenRejectWithTooManyRequests() throws Exception {
        String username = registerUser();

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginPayload(username, "WrongPass123")))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload(username, PASSWORD)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void refreshShouldRotateTheTokenPair() throws Exception {
        String username = registerUser();
        String[] tokens = login(username);
        String accessToken = tokens[0];
        String refreshToken = tokens[1];

        MvcResult refreshResult = refresh(refreshToken);
        String rotatedAccessToken = JsonPath.read(refreshResult.getResponse().getContentAsString(), "$.data.accessToken");
        String rotatedRefreshToken = JsonPath.read(refreshResult.getResponse().getContentAsString(), "$.data.refreshToken");

        assertNotNull(rotatedAccessToken);
        assertNotNull(rotatedRefreshToken);
        assertNotEquals(refreshToken, rotatedRefreshToken);
        assertNotEquals(accessToken, rotatedAccessToken);

        // The rotated access token authenticates against a protected endpoint.
        mockMvc.perform(get("/api/v1/auth/me").header(AUTHORIZATION, "Bearer " + rotatedAccessToken))
                .andExpect(status().isOk());

        // The rotated refresh token keeps the family alive and rotates it again.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshPayload(rotatedRefreshToken)))
                .andExpect(status().isOk());
    }

    /**
     * With the grace window disabled, replaying a rotated token is indistinguishable from
     * theft, so the whole family must die - including the token minted by the legitimate
     * rotation. This is verified end to end (access tokens too) in the Redis-backed suite.
     */
    @Test
    void supersededRefreshTokenIsRejectedAndRevokesTheFamily() throws Exception {
        String username = registerUser();
        String refreshToken = login(username)[1];

        MvcResult refreshResult = refresh(refreshToken);
        String rotatedRefreshToken = JsonPath.read(
                refreshResult.getResponse().getContentAsString(), "$.data.refreshToken");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshPayload(refreshToken)))
                .andExpect(status().isUnauthorized());

        // Reuse detection revoked the family, so the rotated token is dead as well.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshPayload(rotatedRefreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accessTokenCannotBeUsedAtTheRefreshEndpoint() throws Exception {
        String username = registerUser();
        String[] tokens = login(username);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshPayload(tokens[0])))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedAndForgedTokensAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header(AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());

        String forged = Jwts.builder()
                .subject("intruder")
                .claim("roles", List.of("ADMIN"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(
                        "another-secret-key-that-is-long-enough-for-hs256!".getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256)
                .compact();
        mockMvc.perform(get("/api/v1/auth/me").header(AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredAccessTokenIsRejected() throws Exception {
        User user = createUser("expired-user", "PATIENT", AccountStatus.ACTIVE);
        String expired = Jwts.builder()
                .subject(user.getUsername())
                .claim("uid", user.getId().toString())
                .claim("roles", List.of("PATIENT"))
                .claim("type", "access")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get("/api/v1/auth/me").header(AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    /**
     * With revocation disabled by the test profile, logout still revokes refresh tokens
     * (database) but the stateless access token remains valid at auth-service. Access-token
     * revocation is covered by {@code AuthRevocationRedisIntegrationTest}.
     */
    @Test
    void logoutRevokesRefreshToken() throws Exception {
        String username = registerUser();
        String[] tokens = login(username);

        mockMvc.perform(post("/api/v1/auth/logout").header(AUTHORIZATION, "Bearer " + tokens[0]))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshPayload(tokens[1])))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordRequiresCurrentPasswordAndEndsExistingSessions() throws Exception {
        String username = registerUser();
        String[] tokens = login(username);

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header(AUTHORIZATION, "Bearer " + tokens[0])
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"WrongPass123\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header(AUTHORIZATION, "Bearer " + tokens[0])
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk());

        // Existing refresh token is dead.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshPayload(tokens[1])))
                .andExpect(status().isUnauthorized());

        // Old password no longer works; the new one does.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload(username, PASSWORD)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload(username, NEW_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void passwordResetFlowIssuesASingleUseToken() throws Exception {
        String username = registerUser();
        String email = username + "@example.com";

        MvcResult requestResult = mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String token = JsonPath.read(requestResult.getResponse().getContentAsString(), "$.data.token");
        assertNotNull(token);

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk());

        // Single use: replay is rejected.
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload(username, NEW_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void passwordResetRequestDoesNotRevealUnknownAccounts() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody-" + UUID.randomUUID() + "@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").doesNotExist());
    }

    @Test
    void adminCanSuspendAnAccountAndItsTokensStopWorking() throws Exception {
        String adminUsername = "admin" + shortId();
        createUser(adminUsername, "ADMIN", AccountStatus.ACTIVE);
        String adminToken = login(adminUsername)[0];

        String username = registerUser();
        String[] tokens = login(username);
        UUID targetId = userRepository.findByUsername(username).orElseThrow().getId();

        mockMvc.perform(patch("/api/v1/auth/users/" + targetId + "/status")
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"security review\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));

        // Previously issued access token is rejected by the auth-service filter (status check).
        mockMvc.perform(get("/api/v1/auth/me").header(AUTHORIZATION, "Bearer " + tokens[0]))
                .andExpect(status().isUnauthorized());

        // Login is blocked with a status-specific message only after correct credentials.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload(username, PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonAdminCannotManageAccounts() throws Exception {
        String username = registerUser();
        String[] tokens = login(username);
        UUID targetId = userRepository.findByUsername(username).orElseThrow().getId();

        mockMvc.perform(patch("/api/v1/auth/users/" + targetId + "/status")
                        .header(AUTHORIZATION, "Bearer " + tokens[0])
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void roleChangeTakesEffectAndRevokesSessions() throws Exception {
        String adminUsername = "admin" + shortId();
        createUser(adminUsername, "ADMIN", AccountStatus.ACTIVE);
        String adminToken = login(adminUsername)[0];

        String username = registerUser();
        String[] tokens = login(username);
        UUID targetId = userRepository.findByUsername(username).orElseThrow().getId();

        mockMvc.perform(patch("/api/v1/auth/users/" + targetId + "/roles")
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"NURSE\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("NURSE"));

        // The refresh token was revoked by the role change.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshPayload(tokens[1])))
                .andExpect(status().isUnauthorized());

        // A fresh login carries the new role.
        String[] freshTokens = login(username);
        String body = mockMvc.perform(get("/api/v1/auth/me")
                        .header(AUTHORIZATION, "Bearer " + freshTokens[0]))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals("NURSE", ((List<?>) JsonPath.read(body, "$.data.roles")).get(0));
    }

    private String registerUser() throws Exception {
        String username = "user" + shortId();
        String payload = "{\"username\":\"" + username + "\","
                + "\"email\":\"" + username + "@example.com\","
                + "\"password\":\"" + PASSWORD + "\"}";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        return username;
    }

    private User createUser(String username, String roleName, AccountStatus status) {
        Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(
                        Role.builder().name(roleName).description("Test " + roleName + " role").build()));
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .password(passwordEncoder.encode(PASSWORD))
                .status(status)
                .roles(Set.of(role))
                .build();
        return userRepository.save(user);
    }

    private String[] login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String accessToken = JsonPath.read(body, "$.data.accessToken");
        String refreshToken = JsonPath.read(body, "$.data.refreshToken");
        assertEquals("Bearer", JsonPath.read(body, "$.data.tokenType"));
        assertNotNull(accessToken);
        assertNotNull(refreshToken);

        return new String[]{accessToken, refreshToken};
    }

    private MvcResult refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshPayload(refreshToken)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String loginPayload(String username, String password) {
        return "{\"usernameOrEmail\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    private String refreshPayload(String refreshToken) {
        return "{\"refreshToken\":\"" + refreshToken + "\"}";
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
