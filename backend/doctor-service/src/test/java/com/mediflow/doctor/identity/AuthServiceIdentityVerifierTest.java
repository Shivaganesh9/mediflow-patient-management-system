package com.mediflow.doctor.identity;

import com.mediflow.common.exception.ConflictException;
import com.mediflow.common.exception.ServiceUnavailableException;
import com.mediflow.common.resilience.CircuitBreaker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthServiceIdentityVerifierTest {

    private static final String BASE_URL = "http://auth-service:8081";

    private final AtomicReference<String> requestUri = new AtomicReference<>();
    private final AtomicReference<String> authorizationHeader = new AtomicReference<>();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void acceptsActiveIdentityHoldingRequiredRoleAndForwardsCallerToken() {
        AuthServiceIdentityVerifier verifier = verifierReturning(HttpStatus.OK,
                identityBody(UUID.randomUUID(), "ACTIVE", "PATIENT"));
        stubCallerToken("Bearer admin-token");

        assertThatCode(() -> verifier.verifyIdentity(UUID.randomUUID(), "PATIENT"))
                .doesNotThrowAnyException();

        assertThat(requestUri.get()).startsWith(BASE_URL + "/internal/identities/");
        assertThat(authorizationHeader.get()).isEqualTo("Bearer admin-token");
    }

    @Test
    void rejectsUnknownIdentityWithBadRequest() {
        AuthServiceIdentityVerifier verifier = verifierReturning(HttpStatus.NOT_FOUND,
                "{\"success\":false,\"message\":\"User not found\"}");
        stubCallerToken("Bearer admin-token");

        assertThatThrownBy(() -> verifier.verifyIdentity(UUID.randomUUID(), "PATIENT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not reference an existing user");
    }

    @Test
    void rejectsIdentityHoldingAnotherRole() {
        AuthServiceIdentityVerifier verifier = verifierReturning(HttpStatus.OK,
                identityBody(UUID.randomUUID(), "ACTIVE", "DOCTOR"));
        stubCallerToken("Bearer admin-token");

        assertThatThrownBy(() -> verifier.verifyIdentity(UUID.randomUUID(), "PATIENT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not reference a PATIENT identity");
    }

    @Test
    void rejectsInactiveIdentityWithConflict() {
        AuthServiceIdentityVerifier verifier = verifierReturning(HttpStatus.OK,
                identityBody(UUID.randomUUID(), "INACTIVE", "PATIENT"));
        stubCallerToken("Bearer admin-token");

        assertThatThrownBy(() -> verifier.verifyIdentity(UUID.randomUUID(), "PATIENT"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void rejectsLockedIdentityWithConflict() {
        AuthServiceIdentityVerifier verifier = verifierReturning(HttpStatus.OK,
                identityBody(UUID.randomUUID(), "LOCKED", "PATIENT"));
        stubCallerToken("Bearer admin-token");

        assertThatThrownBy(() -> verifier.verifyIdentity(UUID.randomUUID(), "PATIENT"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void mapsForbiddenLookupToAccessDenied() {
        AuthServiceIdentityVerifier verifier = verifierReturning(HttpStatus.FORBIDDEN,
                "{\"success\":false,\"message\":\"Access denied\"}");
        stubCallerToken("Bearer patient-token");

        assertThatThrownBy(() -> verifier.verifyIdentity(UUID.randomUUID(), "PATIENT"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void failsClosedWhenAuthServiceReturnsServerError() {
        AuthServiceIdentityVerifier verifier = verifierReturning(HttpStatus.INTERNAL_SERVER_ERROR,
                "{\"success\":false}");
        stubCallerToken("Bearer admin-token");

        assertThatThrownBy(() -> verifier.verifyIdentity(UUID.randomUUID(), "PATIENT"))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void failsClosedWhenAuthServiceIsUnreachable() {
        ClientHttpRequestFactory failingFactory = (uri, httpMethod) -> {
            throw new IOException("connection refused");
        };
        AuthServiceIdentityVerifier verifier = new AuthServiceIdentityVerifier(
                RestClient.builder().baseUrl(BASE_URL).requestFactory(failingFactory).build());
        stubCallerToken("Bearer admin-token");

        assertThatThrownBy(() -> verifier.verifyIdentity(UUID.randomUUID(), "PATIENT"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("temporarily unavailable");
    }

    @Test
    void opensCircuitBreakerAfterRepeatedTransportFailures() {
        java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        ClientHttpRequestFactory failingFactory = (uri, httpMethod) -> {
            attempts.incrementAndGet();
            throw new IOException("connection refused");
        };
        AuthServiceIdentityVerifier verifier = new AuthServiceIdentityVerifier(
                RestClient.builder().baseUrl(BASE_URL).requestFactory(failingFactory).build());
        stubCallerToken("Bearer admin-token");

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> verifier.verifyIdentity(UUID.randomUUID(), "PATIENT"))
                    .isInstanceOf(ServiceUnavailableException.class);
        }
        // The sixth call fails fast without touching the network.
        assertThatThrownBy(() -> verifier.verifyIdentity(UUID.randomUUID(), "PATIENT"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("temporarily unavailable");
        assertThat(attempts.get()).isEqualTo(5);
    }

    @Test
    void refusesToCallAuthServiceWithoutCallerCredentials() {
        AuthServiceIdentityVerifier verifier = verifierReturning(HttpStatus.OK,
                identityBody(UUID.randomUUID(), "ACTIVE", "PATIENT"));

        assertThatThrownBy(() -> verifier.verifyIdentity(UUID.randomUUID(), "PATIENT"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bearer token");
    }

    @Test
    void missingCallerTokenDoesNotConsumeTheHalfOpenTrialPermit() {
        // One failure opens the breaker; a zero cool-down makes the next call half-open.
        CircuitBreaker breaker = new CircuitBreaker(1, Duration.ZERO);
        breaker.allowRequest();
        breaker.recordFailure();
        AuthServiceIdentityVerifier verifier = verifierReturning(HttpStatus.OK,
                identityBody(UUID.randomUUID(), "ACTIVE", "PATIENT"), breaker);

        // No caller token: a caller error, so the half-open trial must remain available.
        assertThatThrownBy(() -> verifier.verifyIdentity(UUID.randomUUID(), "PATIENT"))
                .isInstanceOf(IllegalStateException.class);

        stubCallerToken("Bearer admin-token");
        assertThatCode(() -> verifier.verifyIdentity(UUID.randomUUID(), "PATIENT"))
                .doesNotThrowAnyException();
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private AuthServiceIdentityVerifier verifierReturning(HttpStatus status, String body) {
        return verifierReturning(status, body, null);
    }

    private AuthServiceIdentityVerifier verifierReturning(HttpStatus status, String body,
                                                         CircuitBreaker breaker) {
        MockClientHttpResponse response =
                new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ClientHttpRequestFactory factory = (uri, httpMethod) -> {
            MockClientHttpRequest delegate = new MockClientHttpRequest(httpMethod, uri);
            delegate.setResponse(response);
            return new CapturingRequest(delegate);
        };
        RestClient restClient = RestClient.builder().baseUrl(BASE_URL).requestFactory(factory).build();
        return breaker == null
                ? new AuthServiceIdentityVerifier(restClient)
                : new AuthServiceIdentityVerifier(restClient, breaker);
    }

    /**
     * Captures what RestClient actually sent. Headers are written after the request is created,
     * so they can only be inspected in {@code execute()}.
     */
    private final class CapturingRequest implements ClientHttpRequest {

        private final MockClientHttpRequest delegate;

        CapturingRequest(MockClientHttpRequest delegate) {
            this.delegate = delegate;
        }

        @Override
        public HttpMethod getMethod() {
            return delegate.getMethod();
        }

        @Override
        public URI getURI() {
            return delegate.getURI();
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public java.util.Map<String, Object> getAttributes() {
            return delegate.getAttributes();
        }

        @Override
        public OutputStream getBody() throws IOException {
            return delegate.getBody();
        }

        @Override
        public ClientHttpResponse execute() throws IOException {
            requestUri.set(delegate.getURI().toString());
            authorizationHeader.set(delegate.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            return delegate.execute();
        }
    }

    private String identityBody(UUID userId, String status, String role) {
        return "{\"success\":true,\"message\":\"Identity retrieved successfully\",\"data\":{"
                + "\"userId\":\"" + userId + "\","
                + "\"username\":\"someone\","
                + "\"status\":\"" + status + "\","
                + "\"roles\":[\"" + role + "\"]}}";
    }

    private void stubCallerToken(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
