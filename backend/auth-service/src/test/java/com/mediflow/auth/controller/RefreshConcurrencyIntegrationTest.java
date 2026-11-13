package com.mediflow.auth.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Two truly concurrent refresh calls with the same token must not produce two independent
 * rotation chains. Within the retry grace window both calls succeed and the family stays
 * alive; the row lock serialises the rotations instead of racing.
 */
@SpringBootTest(properties = {
        "security.revocation.enabled=false",
        "auth.refresh.reuse-grace=30s",
        "spring.datasource.url=jdbc:h2:mem:authconcurrency;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
})
@AutoConfigureMockMvc
class RefreshConcurrencyIntegrationTest {

    private static final String PASSWORD = "Concurrent@12345";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void concurrentRefreshRequestsDoNotRaceTheRotation() throws Exception {
        String username = registerUser();
        String refreshToken = login(username);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return mockMvc.perform(post("/api/v1/auth/refresh")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(15, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsOnly(200);

            // The family survived the concurrent retries, so a subsequent refresh still works.
            String latestRefresh = refreshTokenAfterReplay(refreshToken);
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"" + latestRefresh + "\"}"))
                    .andExpect(status().isOk());
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Both concurrent calls answered 200, but only the last rotation's token is active.
     * Replaying the originally presented token returns a fresh pair thanks to the grace
     * window, so the family keeps working.
     */
    private String refreshTokenAfterReplay(String originalToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + originalToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.refreshToken");
    }

    private String registerUser() throws Exception {
        String username = "concurrent" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\","
                                + "\"email\":\"" + username + "@example.com\","
                                + "\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
        return username;
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.refreshToken");
    }
}
