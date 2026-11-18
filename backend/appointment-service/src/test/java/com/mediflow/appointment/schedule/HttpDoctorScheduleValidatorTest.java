package com.mediflow.appointment.schedule;

import com.mediflow.common.exception.ConflictException;
import com.mediflow.common.exception.ResourceNotFoundException;
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
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpDoctorScheduleValidatorTest {

    private static final String BASE_URL = "http://doctor-service:8085";
    private static final LocalDateTime START = LocalDateTime.of(2031, 3, 3, 10, 0);
    private static final LocalDateTime END = LocalDateTime.of(2031, 3, 3, 10, 30);

    private final AtomicReference<String> requestUri = new AtomicReference<>();
    private final AtomicReference<String> authorizationHeader = new AtomicReference<>();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void acceptsASlotInsideWorkingHoursAndForwardsTheCallerToken() {
        HttpDoctorScheduleValidator validator = validatorReturning(HttpStatus.OK, validationBody(true, null));
        stubCallerToken("Bearer patient-token");

        assertThatCode(() -> validator.validateSlot(UUID.randomUUID(), START, END))
                .doesNotThrowAnyException();

        assertThat(requestUri.get())
                .startsWith(BASE_URL + "/internal/doctors/")
                .contains("/schedule/validation")
                .contains("startTime=2031-03-03T10:00")
                .contains("endTime=2031-03-03T10:30");
        assertThat(authorizationHeader.get()).isEqualTo("Bearer patient-token");
    }

    @Test
    void rejectsSlotsOutsideWorkingHoursWithConflict() {
        HttpDoctorScheduleValidator validator = validatorReturning(HttpStatus.OK,
                validationBody(false, "The requested slot is outside the doctor's working hours (09:00-17:00)"));
        stubCallerToken("Bearer patient-token");

        assertThatThrownBy(() -> validator.validateSlot(UUID.randomUUID(), START, END))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("working hours");
    }

    @Test
    void rejectsSlotsOverlappingLeaveWithConflict() {
        HttpDoctorScheduleValidator validator = validatorReturning(HttpStatus.OK,
                validationBody(false, "The doctor is on leave on 2031-03-03. Please choose another date."));
        stubCallerToken("Bearer patient-token");

        assertThatThrownBy(() -> validator.validateSlot(UUID.randomUUID(), START, END))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("on leave");
    }

    @Test
    void usesASafeFallbackReasonWhenDoctorServiceOmitsIt() {
        HttpDoctorScheduleValidator validator = validatorReturning(HttpStatus.OK, validationBody(false, null));
        stubCallerToken("Bearer patient-token");

        assertThatThrownBy(() -> validator.validateSlot(UUID.randomUUID(), START, END))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("outside the doctor's working hours");
    }

    @Test
    void mapsUnknownDoctorToNotFound() {
        HttpDoctorScheduleValidator validator = validatorReturning(HttpStatus.NOT_FOUND,
                "{\"success\":false,\"message\":\"Doctor not found\"}");
        stubCallerToken("Bearer patient-token");

        assertThatThrownBy(() -> validator.validateSlot(UUID.randomUUID(), START, END))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void mapsForbiddenValidationToAccessDenied() {
        HttpDoctorScheduleValidator validator = validatorReturning(HttpStatus.FORBIDDEN,
                "{\"success\":false,\"message\":\"Access denied\"}");
        stubCallerToken("Bearer patient-token");

        assertThatThrownBy(() -> validator.validateSlot(UUID.randomUUID(), START, END))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void failsClosedWhenDoctorServiceReturnsServerError() {
        HttpDoctorScheduleValidator validator = validatorReturning(HttpStatus.INTERNAL_SERVER_ERROR,
                "{\"success\":false}");
        stubCallerToken("Bearer patient-token");

        assertThatThrownBy(() -> validator.validateSlot(UUID.randomUUID(), START, END))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void failsClosedWhenDoctorServiceIsUnreachable() {
        ClientHttpRequestFactory failingFactory = (uri, httpMethod) -> {
            throw new IOException("connection refused");
        };
        HttpDoctorScheduleValidator validator = new HttpDoctorScheduleValidator(
                RestClient.builder().baseUrl(BASE_URL).requestFactory(failingFactory).build());
        stubCallerToken("Bearer patient-token");

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
                RestClient.builder().baseUrl(BASE_URL).requestFactory(failingFactory).build());
        stubCallerToken("Bearer patient-token");

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
    void refusesToCallDoctorServiceWithoutCallerCredentials() {
        HttpDoctorScheduleValidator validator = validatorReturning(HttpStatus.OK, validationBody(true, null));

        assertThatThrownBy(() -> validator.validateSlot(UUID.randomUUID(), START, END))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bearer token");
    }

    @Test
    void missingCallerTokenDoesNotConsumeTheHalfOpenTrialPermit() {
        CircuitBreaker breaker = new CircuitBreaker(1, Duration.ZERO);
        breaker.allowRequest();
        breaker.recordFailure();
        HttpDoctorScheduleValidator validator = validatorReturning(HttpStatus.OK, validationBody(true, null), breaker);

        assertThatThrownBy(() -> validator.validateSlot(UUID.randomUUID(), START, END))
                .isInstanceOf(IllegalStateException.class);

        stubCallerToken("Bearer patient-token");
        assertThatCode(() -> validator.validateSlot(UUID.randomUUID(), START, END))
                .doesNotThrowAnyException();
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private HttpDoctorScheduleValidator validatorReturning(HttpStatus status, String body) {
        return validatorReturning(status, body, null);
    }

    private HttpDoctorScheduleValidator validatorReturning(HttpStatus status, String body, CircuitBreaker breaker) {
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
                ? new HttpDoctorScheduleValidator(restClient)
                : new HttpDoctorScheduleValidator(restClient, breaker);
    }

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

    private String validationBody(boolean valid, String reason) {
        String reasonJson = reason == null ? "null" : "\"" + reason + "\"";
        return "{\"success\":true,\"message\":\"Schedule validation completed\",\"data\":{"
                + "\"valid\":" + valid + ",\"reason\":" + reasonJson + "}}";
    }

    private void stubCallerToken(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
