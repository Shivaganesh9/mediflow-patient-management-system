package com.mediflow.auth.controller;

import com.jayway.jsonpath.JsonPath;
import com.mediflow.auth.entity.Role;
import com.mediflow.auth.entity.User;
import com.mediflow.auth.repository.RoleRepository;
import com.mediflow.auth.repository.UserRepository;
import com.mediflow.common.enums.AccountStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises access-token revocation against a real Redis instance, so the same
 * {@code TokenRevocationRegistry} contract used by every downstream service is tested
 * instead of being mocked away.
 */
@SpringBootTest(properties = {
        "security.revocation.enabled=true",
        "auth.refresh.reuse-grace=0s",
        "spring.datasource.url=jdbc:h2:mem:authrevocation;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AuthRevocationRedisIntegrationTest {

    private static final String PASSWORD = "Revoke@12345";
    private static final String NEW_PASSWORD = "Rotated#98765";
    private static final String AUTHORIZATION = "Authorization";

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void logoutRevokesTheAccessTokenSoDownstreamServicesRejectIt() throws Exception {
        String username = registerUser();
        String[] tokens = login(username);

        mockMvc.perform(get("/api/v1/auth/me").header(AUTHORIZATION, "Bearer " + tokens[0]))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout").header(AUTHORIZATION, "Bearer " + tokens[0]))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me").header(AUTHORIZATION, "Bearer " + tokens[0]))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshPayload(tokens[1])))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenReuseRevokesTheWholeFamilyIncludingAccessTokens() throws Exception {
        String username = registerUser();
        String[] first = login(username);

        MvcResult rotation = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshPayload(first[1])))
                .andExpect(status().isOk())
                .andReturn();
        String rotatedBody = rotation.getResponse().getContentAsString();
        String rotatedAccess = JsonPath.read(rotatedBody, "$.data.accessToken");
        String rotatedRefresh = JsonPath.read(rotatedBody, "$.data.refreshToken");

        // Replaying the already-rotated token is treated as theft (grace window is 0 here).
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshPayload(first[1])))
                .andExpect(status().isUnauthorized());

        // The family-wide revocation also killed the token minted by the attacker-free rotation.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshPayload(rotatedRefresh)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me").header(AUTHORIZATION, "Bearer " + rotatedAccess))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordChangeRevokesEverySessionAndEveryAccessToken() throws Exception {
        String username = registerUser();
        String[] firstSession = login(username);
        String[] secondSession = login(username);

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header(AUTHORIZATION, "Bearer " + firstSession[0])
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me").header(AUTHORIZATION, "Bearer " + firstSession[0]))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me").header(AUTHORIZATION, "Bearer " + secondSession[0]))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshPayload(secondSession[1])))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload(username, NEW_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void suspensionRevokesPreviouslyIssuedTokensThroughTheSharedStore() throws Exception {
        String adminUsername = "revadmin" + shortId();
        createUser(adminUsername, "ADMIN");
        String adminToken = login(adminUsername)[0];

        String username = registerUser();
        String[] tokens = login(username);
        UUID targetId = userRepository.findByUsername(username).orElseThrow().getId();

        mockMvc.perform(patch("/api/v1/auth/users/" + targetId + "/status")
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me").header(AUTHORIZATION, "Bearer " + tokens[0]))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshPayload(tokens[1])))
                .andExpect(status().isUnauthorized());
    }

    private String registerUser() throws Exception {
        String username = "revuser" + shortId();
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\","
                                + "\"email\":\"" + username + "@example.com\","
                                + "\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
        return username;
    }

    private User createUser(String username, String roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(
                        Role.builder().name(roleName).description("Test " + roleName).build()));
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password(passwordEncoder.encode(PASSWORD))
                .status(AccountStatus.ACTIVE)
                .roles(Set.of(role))
                .build());
    }

    private String[] login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return new String[]{
                JsonPath.read(body, "$.data.accessToken"),
                JsonPath.read(body, "$.data.refreshToken")
        };
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
