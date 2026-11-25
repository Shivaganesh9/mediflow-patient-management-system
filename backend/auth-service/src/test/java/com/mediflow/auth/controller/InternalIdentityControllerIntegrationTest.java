package com.mediflow.auth.controller;

import com.mediflow.auth.entity.Role;
import com.mediflow.auth.entity.User;
import com.mediflow.auth.repository.RoleRepository;
import com.mediflow.auth.repository.UserRepository;
import com.mediflow.common.enums.AccountStatus;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the internal identity lookup consumed by patient-service and doctor-service.
 * The endpoint must stay authenticated, non-public and free of sensitive user data.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InternalIdentityControllerIntegrationTest {

    private static final String SECRET = "test-secret-key-for-auth-service-with-sufficient-length";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @BeforeEach
    void setUp() {
        ensureUser("internal-admin", "ADMIN", AccountStatus.ACTIVE);
        ensureUser("internal-doctor", "DOCTOR", AccountStatus.ACTIVE);
        ensureUser("internal-nurse", "NURSE", AccountStatus.ACTIVE);
        ensureUser("internal-billing", "BILLING_STAFF", AccountStatus.ACTIVE);
        ensureUser("internal-support", "SUPPORT", AccountStatus.ACTIVE);
    }

    @Test
    void adminResolvesExistingIdentityWithoutSensitiveFields() throws Exception {
        UUID patientId = ensureUser("internal-patient", "PATIENT", AccountStatus.ACTIVE);

        mockMvc.perform(get("/internal/identities/" + patientId)
                        .header("Authorization", "Bearer " + token("internal-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(patientId.toString()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.roles", hasItem("PATIENT")))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.email").doesNotExist());
    }

    @Test
    void doctorCallerCanResolveIdentity() throws Exception {
        UUID patientId = ensureUser("internal-patient", "PATIENT", AccountStatus.ACTIVE);

        mockMvc.perform(get("/internal/identities/" + patientId)
                        .header("Authorization", "Bearer " + token("internal-doctor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(patientId.toString()));
    }

    @Test
    void inactiveIdentityIsReportedWithItsStatus() throws Exception {
        UUID inactiveId = ensureUser("internal-inactive", "PATIENT", AccountStatus.INACTIVE);

        mockMvc.perform(get("/internal/identities/" + inactiveId)
                        .header("Authorization", "Bearer " + token("internal-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
    }

    @Test
    void unknownIdentityReturnsNotFound() throws Exception {
        mockMvc.perform(get("/internal/identities/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token("internal-admin")))
                .andExpect(status().isNotFound());
    }

    @Test
    void patientCallerIsForbidden() throws Exception {
        UUID patientId = ensureUser("internal-patient", "PATIENT", AccountStatus.ACTIVE);

        mockMvc.perform(get("/internal/identities/" + patientId)
                        .header("Authorization", "Bearer " + token("internal-patient")))
                .andExpect(status().isForbidden());
    }

    /**
     * Profile provisioning is an ADMIN/DOCTOR duty. Operational roles that only read
     * appointments or invoices must not be able to enumerate identities.
     */
    @Test
    void operationalRolesAreForbidden() throws Exception {
        UUID patientId = ensureUser("internal-patient", "PATIENT", AccountStatus.ACTIVE);

        for (String username : List.of("internal-nurse", "internal-billing", "internal-support")) {
            mockMvc.perform(get("/internal/identities/" + patientId)
                            .header("Authorization", "Bearer " + token(username)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void suspendedStaffCallerIsRejected() throws Exception {
        ensureUser("internal-suspended-admin", "ADMIN", AccountStatus.SUSPENDED);
        UUID patientId = ensureUser("internal-patient", "PATIENT", AccountStatus.ACTIVE);

        mockMvc.perform(get("/internal/identities/" + patientId)
                        .header("Authorization", "Bearer " + token("internal-suspended-admin")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/internal/identities/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    private UUID ensureUser(String username, String roleName, AccountStatus status) {
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElseGet(() -> {
                    Role role = roleRepository.findByName(roleName)
                            .orElseGet(() -> roleRepository.save(
                                    Role.builder().name(roleName).description("Test " + roleName + " role").build()));
                    User user = User.builder()
                            .username(username)
                            .email(username + "@mediflow.test")
                            .password("$2a$10$test-hash-not-used-for-login")
                            .status(status)
                            .roles(Set.of(role))
                            .build();
                    return userRepository.save(user).getId();
                });
    }

    /**
     * The auth-service filter reloads the account and its roles from the database, so the token
     * only needs a valid signature and subject. That is also why the forbidden-role cases above
     * can rely on real users instead of forged role claims.
     */
    private String token(String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }
}
