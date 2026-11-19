package com.mediflow.doctor.security;

import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the container's ERROR dispatch against Spring Security.
 *
 * <p>When the filter chain denies a request, Spring Security calls {@code sendError(403)}, which the
 * servlet container turns into an ERROR dispatch to {@code /error}. Because
 * {@code OncePerRequestFilter} skips error dispatches, the JWT filter does not run there, and
 * without an explicit rule the anonymous {@code /error} request was rejected by
 * {@code anyRequest().authenticated()}. The entry point then replaced the pending 403 with a 401
 * (and an empty body), so clients could not distinguish "forbidden" from "unauthenticated".</p>
 *
 * <p>These tests pin both halves of the fix: the ERROR dispatch is permitted, while ordinary
 * requests still require authentication.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "jwt.secret=test-secret-key-for-error-dispatch-security")
class ErrorDispatchSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void permitsTheContainerErrorDispatch() throws Exception {
        // Without `.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()` this returns 401:
        // the error page request is treated as a new, anonymous user request.
        mockMvc.perform(get("/api/v1/doctors").with(request -> {
                    request.setDispatcherType(DispatcherType.ERROR);
                    return request;
                }))
                .andExpect(status().isOk());
    }

    @Test
    void stillRequiresAuthenticationForOrdinaryRequests() throws Exception {
        mockMvc.perform(get("/api/v1/doctors"))
                .andExpect(status().isUnauthorized());
    }
}
