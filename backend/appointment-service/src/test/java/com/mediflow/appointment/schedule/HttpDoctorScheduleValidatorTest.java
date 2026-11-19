package com.mediflow.appointment.schedule;

import com.mediflow.common.exception.ConflictException;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.common.exception.ServiceUnavailableException;
import com.mediflow.common.resilience.CircuitBreaker;
import com.mediflow.common.security.InternalServiceToken;
import com.mediflow.common.security.JwtClaims;
import io.jsonwebtoken.Claims;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpDoctorScheduleValidatorTest {

    private static final String BASE_URL = "http://doctor-service:8085";
    private static final String SECRET = "test-secret-key-for-appointment-service-with-sufficient-length";
    private static final LocalDateTime START = LocalDateTime.of(2031, 3, 3, 10, 0);
    private static final LocalDateTime END = LocalDateTime.of(2031, 3, 3, 10, 30);

    private final AtomicReference<String> requestUri = new AtomicReference<>();
    private final AtomicReference<String> authorizationHeader = new AtomicReference<>();
    private final InternalServiceTokenProvider tokenProvider = new InternalServiceTokenProvider(SECRET);

    @Test
    void acceptsASlotInsideWorkingHoursAndSendsAServiceCredential() {
        HttpDoctorScheduleValidator validator = validatorReturning(HttpStatus.OK, validationBody(true, null));

        assertThatCode(() -> validator.validateSlot(UUID.randomUUID(), START, END))
                .doesNotThrowAnyException();

        assertThat(requestUri.get())
                .startsWith(BASE_URL + "/internal/doctors/")
                .contains("/schedule/validation")
                .contains("startTime=2031-03-03T10:00")
                .contains("endTime=2031-03-03T10:30");

        String header = authorizationHeader.get();
        assertThat(header).startsWith("Bearer ");
        Claims claims = InternalServiceToken.parse(SECRET, header.substring("Bearer ".length()));
        assertThat(claims.get(JwtClaims.TYPE, String.class)).isEqualTo(InternalServiceToken.TYPE_SERVICE);
        assertThat(claims.getIssuer()).isEqualTo(InternalServiceToken.ISSUER_APPOINTMENT_SERVICE);
        assertThat(claims.getAudience()).contains(InternalServiceToken.AUDIENCE_DOCTOR_SERVICE);
        assertThat(claims.get(InternalServiceToken.CLAIM_SCOPE, String.class))
                .isEqualTo(InternalServiceToken.SCOPE_DOCTOR_SCHEDULE_VALIDATE);
        // One call only: short-lived, and unique per request.
        assertThat(claims.getExpiration().toInstant())
                .isBefore(Instant.now().plus(Duration.ofSeconds(61)));
        assertThat(claims.getId()).isNotBlank();
    }

    @Test
    void mintsAFreshCredentialForEveryCall() {
        HttpDoctorScheduleValidator validator = validatorReturning(HttpStatus.OK, validationBody(true, null));

        validator.validateSlot(UUID.randomUUID(), START, END);
        String first = authorizationHeader.get();
        validator.validateSlot(UUID.randomUUID(), START, END);
        String second = authorizationHeader.get();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void rejectsSlotsOutsideWorkingHoursWithConflict() {
        HttpDoctorScheduleValidator validator = validatorReturning(HttpStatus.OK,
                validationBody(false, "The requested slot is outside the doctor's working hours (09:00-17:00)"));

        assertThatThrownBy(() -> validator.validateSlot(UUID.randomUUID(), START, END))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("working hours");
    }

    @Test
    void rejectsSlotsOverlappingLeaveWithConflict() {
        HttpDoctorScheduleValidator validator = validatorReturning(HttpStatus.OK,
                validationBody(false, "The doctor is on leave on 2031-03-03. Please choose another date."));

        assertThatThrownBy(() -> validator.validateSlot(UUID.randomUUID(), START, END))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("on leave");
    }

    @Test
    void usesASafeFallbackReasonWhenDoctorServiceOmitsIt() {
        HttpDoctorScheduleValidator validator = validatorReturning(HttpStatus.OK, validationBody(false, null));

        assertThatThrownBy(() -> validator.validateSlot(UUID.randomUUID(), START, END))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("outside the doctor's working hours");
    }

    @Test
    void mapsUnknownDoctorToNotFound() {
        HttpDoctorScheduleValidator validator = validatorReturning(HttpStatus.NOT_FOUND,
                "{\"success\":false,\"message\":\"Doctor not found\"}");

        assertThatThrownBy(() -> validator.validateSlot(UUID.randomUUID(), START, END))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void mapsForbiddenValidationToAccessDenied() {
        HttpDoctorScheduleValidator validator = validatorReturning(HttpStatus.FORBIDDEN,
                "{\"success\":false,\"message\":\"Access denied\"}");

        assertThatThrownBy(() -> validator.validateSlot(UUID.randomUUID(), START, END))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Not allowed");
    }

    @Test
    void failsClosedWhenDoctorServiceReturnsServerError() {
        HttpDoctorScheduleValidator validator = validatorReturning(HttpStatus.INTERNAL_SERVER_ERROR,
                "{\"success\":false}");

        assertThatThrownBy(() -> validator.validateSlot(UUID.randomUUID(), START, END))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void failsClosedWhenDoctorServiceIsUnreachable() {
        ClientHttpRequestFactory failingFactory = (uri, httpMethod) -> {
            throw new IOException("connection refused");
        };
        HttpDoctorScheduleValidator validator = new HttpDoctorScheduleValidator(
                RestClient.builder().baseUrl(BASE_URL).requestFactory(failingFactory).build(), tokenProvider);

        assertThatThrownBy(() -> validator.validateSlot(UUID.randomUUID(), START, END))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("cannot be verified");
    }

    @Test
    void opensTheCircuitBreakerAfterRepeatedTransportFailures() {
        AtomicInteger attempts = new AtomicInteger();
        ClientHttpRequestFactory failingFactory = (uri, httpMethod) -> {
            attempts.incrementAndGet();
            throw new IOException("connection refused");
        };
        HttpDoctorScheduleValidator validator = new HttpDoctorScheduleValidator(
                RestClient.builder().baseUrl(BASE_URL).requestFactory(failingFactory).build(), tokenProvider);

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> validator.validateSlot(UUID.randomUUID(), START, END))
                    .isInstanceOf(ServiceUnavailableException.class);
        }
        assertThatThrownBy(() -> validator.validateSlot(UUID.randomUUID(), START, END))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("cannot be verified");
        assertThat(attempts.get()).isEqualTo(5);
    }

    @Test
    void rejectsAnAlreadyOpenBreakerWithoutCallingDoctorService() {
        CircuitBreaker breaker = new CircuitBreaker(1, Duration.ofSeconds(30));
        breaker.allowRequest();
        breaker.recordFailure();
        AtomicInteger attempts = new AtomicInteger();
        ClientHttpRequestFactory factory = (uri, httpMethod) -> {
            attempts.incrementAndGet();
            throw new IOException("must not be called");
        };
        HttpDoctorScheduleValidator validator = new HttpDoctorScheduleValidator(
                RestClient.builder().baseUrl(BASE_URL).requestFactory(factory).build(), breaker, tokenProvider);

        assertThatThrownBy(() -> validator.validateSlot(UUID.randomUUID(), START, END))
                .isInstanceOf(ServiceUnavailableException.class);
        assertThat(attempts.get()).isZero();
    }

    private HttpDoctorScheduleValidator validatorReturning(HttpStatus status, String body) {
        return validatorReturning(status, body, null);
    }

    private HttpDoctorScheduleValidator validatorReturning(HttpStatus status, String body, CircuitBreaker breaker) {
        ClientHttpRequestFactory factory = (uri, httpMethod) -> {
            MockClientHttpRequest delegate = new MockClientHttpRequest(httpMethod, uri);
            // A MockClientHttpResponse body is single-use, so a fresh response is built per attempt;
            // this keeps tests that call the validator more than once (fresh-credential checks) working.
            return new CapturingRequest(delegate, status, body);
        };
        RestClient restClient = RestClient.builder().baseUrl(BASE_URL).requestFactory(factory).build();
        return breaker == null
                ? new HttpDoctorScheduleValidator(restClient, tokenProvider)
                : new HttpDoctorScheduleValidator(restClient, breaker, tokenProvider);
    }

    private final class CapturingRequest implements ClientHttpRequest {

        private final MockClientHttpRequest delegate;
        private final HttpStatus status;
        private final String body;

        CapturingRequest(MockClientHttpRequest delegate, HttpStatus status, String body) {
            this.delegate = delegate;
            this.status = status;
            this.body = body;
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
            MockClientHttpResponse response =
                    new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), status);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return response;
        }
    }

    private String validationBody(boolean valid, String reason) {
        String reasonJson = reason == null ? "null" : "\"" + reason + "\"";
        return "{\"success\":true,\"message\":\"Schedule validation completed\",\"data\":{"
                + "\"valid\":" + valid + ",\"reason\":" + reasonJson + "}}";
    }
}
