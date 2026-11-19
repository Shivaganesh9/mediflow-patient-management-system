package com.mediflow.appointment.schedule;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mediflow.common.exception.ConflictException;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.common.exception.ServiceUnavailableException;
import com.mediflow.common.logging.CorrelationContext;
import com.mediflow.common.resilience.CircuitBreaker;
import com.mediflow.common.response.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Synchronous doctor-service schedule check used when an appointment is booked or rescheduled.
 *
 * <p>The call is authenticated with a narrow, short-lived service credential minted by
 * {@link InternalServiceTokenProvider} (doctor-service only accepts the
 * {@code doctor-schedule:validate} scope on {@code /internal/**}), the correlation id keeps both
 * sides of the call traceable, and the call is bounded: short timeouts, no retries and a circuit
 * breaker. Any transport/5xx failure becomes {@code 503} so the booking fails closed rather than
 * silently skipping the working-hours and leave rules.</p>
 */
@Component
public class HttpDoctorScheduleValidator implements DoctorScheduleValidator {

    private static final String VALIDATION_PATH = "/internal/doctors/{doctorId}/schedule/validation";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);
    private static final int FAILURE_THRESHOLD = 5;
    private static final Duration OPEN_DURATION = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final InternalServiceTokenProvider serviceTokenProvider;

    @Autowired
    public HttpDoctorScheduleValidator(
            RestClient.Builder restClientBuilder,
            @Value("${services.doctor-service-url:http://localhost:8085}") String doctorServiceUrl,
            @Value("${services.doctor-service-connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${services.doctor-service-read-timeout-ms:3000}") int readTimeoutMs,
            InternalServiceTokenProvider serviceTokenProvider) {
        this(buildRestClient(restClientBuilder, doctorServiceUrl, connectTimeoutMs, readTimeoutMs),
                serviceTokenProvider);
    }

    /** Test seam: allows exercising the validator against a stubbed {@link RestClient}. */
    HttpDoctorScheduleValidator(RestClient restClient, InternalServiceTokenProvider serviceTokenProvider) {
        this(restClient, new CircuitBreaker(FAILURE_THRESHOLD, OPEN_DURATION), serviceTokenProvider);
    }

    /** Test seam: lets a test drive breaker transitions with a fast threshold/cool-down. */
    HttpDoctorScheduleValidator(RestClient restClient, CircuitBreaker circuitBreaker,
                                InternalServiceTokenProvider serviceTokenProvider) {
        this.restClient = restClient;
        this.circuitBreaker = circuitBreaker;
        this.serviceTokenProvider = serviceTokenProvider;
    }

    private static RestClient buildRestClient(RestClient.Builder builder, String doctorServiceUrl,
                                              int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs > 0 ? connectTimeoutMs : (int) CONNECT_TIMEOUT.toMillis());
        requestFactory.setReadTimeout(readTimeoutMs > 0 ? readTimeoutMs : (int) READ_TIMEOUT.toMillis());
        return builder.baseUrl(doctorServiceUrl).requestFactory(requestFactory).build();
    }

    @Override
    public void validateSlot(UUID doctorId, LocalDateTime startTime, LocalDateTime endTime) {
        String authorization = "Bearer " + serviceTokenProvider.doctorScheduleValidationToken();
        if (!circuitBreaker.allowRequest()) {
            throw new ServiceUnavailableException(
                    "Doctor availability cannot be verified right now. Please retry shortly.");
        }
        try {
            RestClient.RequestHeadersSpec<?> request = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(VALIDATION_PATH)
                            .queryParam("startTime", TIME_FORMAT.format(startTime))
                            .queryParam("endTime", TIME_FORMAT.format(endTime))
                            .build(doctorId))
                    .header(HttpHeaders.AUTHORIZATION, authorization);
            String correlationId = CorrelationContext.current();
            if (correlationId != null) {
                request = request.header(CorrelationContext.HEADER, correlationId);
            }

            ApiResponse<ScheduleValidation> response = request.retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<ScheduleValidation>>() { });

            if (response == null || response.getData() == null) {
                circuitBreaker.recordFailure();
                throw new ServiceUnavailableException(
                        "Doctor availability returned an empty response. Please retry shortly.");
            }
            circuitBreaker.recordSuccess();
            if (!response.getData().valid()) {
                throw new ConflictException(reasonOrDefault(response.getData().reason()));
            }
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 404) {
                // A healthy "not found" is a caller mistake, not a dependency failure.
                circuitBreaker.recordSuccess();
                throw new ResourceNotFoundException("Doctor not found with id: " + doctorId);
            }
            if (status == 401 || status == 403) {
                circuitBreaker.recordSuccess();
                throw new AccessDeniedException(
                        "Not allowed to verify the doctor's availability");
            }
            circuitBreaker.recordFailure();
            throw new ServiceUnavailableException(
                    "Doctor availability cannot be verified right now. Please retry shortly.", ex);
        } catch (RestClientException ex) {
            circuitBreaker.recordFailure();
            throw new ServiceUnavailableException(
                    "Doctor availability cannot be verified right now. Please retry shortly.", ex);
        }
    }

    private String reasonOrDefault(String reason) {
        return reason == null || reason.isBlank()
                ? "The requested slot is outside the doctor's working hours"
                : reason;
    }

    /** Subset of the doctor-service validation payload; it contains no patient data. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScheduleValidation(boolean valid, String reason) {
    }
}
