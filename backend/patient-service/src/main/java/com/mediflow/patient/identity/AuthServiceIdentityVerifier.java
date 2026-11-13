package com.mediflow.patient.identity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mediflow.common.enums.AccountStatus;
import com.mediflow.common.exception.ConflictException;
import com.mediflow.common.exception.ServiceUnavailableException;
import com.mediflow.common.response.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Validates profile references against auth-service, the only service that owns user identities.
 *
 * <p>Design decisions:</p>
 * <ul>
 *   <li><strong>No shared database access</strong>: services keep separate databases, so the identity
 *       is resolved through the dedicated internal endpoint {@code /internal/identities/{userId}}.</li>
 *   <li><strong>Caller credentials are forwarded</strong> (the admin/staff JWT): auth-service authorizes
 *       the lookup itself, so a compromised caller path cannot use this service to enumerate identities
 *       with a patient token.</li>
 *   <li><strong>Fail closed</strong>: an unreachable or misbehaving auth-service yields 503 instead of
 *       creating a profile against an unverified identity.</li>
 *   <li>Short timeouts keep profile creation responsive when auth-service is degraded.</li>
 * </ul>
 */
@Component
public class AuthServiceIdentityVerifier implements IdentityVerifier {

    private static final String IDENTITY_PATH = "/internal/identities/{userId}";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;

    @Autowired
    public AuthServiceIdentityVerifier(RestClient.Builder restClientBuilder,
                                       @Value("${services.auth-service-url:http://localhost:8081}") String authServiceUrl) {
        this(buildRestClient(restClientBuilder, authServiceUrl));
    }

    /** Test seam: allows exercising the verifier against a stubbed {@link RestClient}. */
    AuthServiceIdentityVerifier(RestClient restClient) {
        this.restClient = restClient;
    }

    private static RestClient buildRestClient(RestClient.Builder restClientBuilder, String authServiceUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        requestFactory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        return restClientBuilder
                .baseUrl(authServiceUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public void verifyIdentity(UUID userId, String requiredRole) {
        Identity identity = lookup(userId);

        if (identity.status() != AccountStatus.ACTIVE) {
            throw new ConflictException("The referenced user account is not active");
        }
        if (identity.roles() == null || !identity.roles().contains(requiredRole)) {
            throw new IllegalArgumentException("userId does not reference a " + requiredRole + " identity");
        }
    }

    private Identity lookup(UUID userId) {
        try {
            ApiResponse<Identity> response = restClient.get()
                    .uri(IDENTITY_PATH, userId)
                    .header(HttpHeaders.AUTHORIZATION, callerAuthorizationHeader())
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<Identity>>() { });

            if (response == null || response.getData() == null || response.getData().userId() == null) {
                throw new ServiceUnavailableException("Identity verification returned an empty response");
            }
            return response.getData();
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 404) {
                throw new IllegalArgumentException("userId does not reference an existing user");
            }
            if (status == 401 || status == 403) {
                throw new AccessDeniedException("Not allowed to verify the referenced user identity");
            }
            throw new ServiceUnavailableException("Identity verification is temporarily unavailable", ex);
        } catch (ResourceAccessException ex) {
            throw new ServiceUnavailableException("Identity verification is temporarily unavailable", ex);
        }
    }

    private String callerAuthorizationHeader() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            String authorization = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null && authorization.startsWith("Bearer ")) {
                return authorization;
            }
        }
        throw new IllegalStateException("Cannot verify an identity without the caller's bearer token");
    }

    /** Subset of the auth-service identity payload; no credentials are consumed or logged. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Identity(UUID userId, String username, AccountStatus status, Set<String> roles) {
    }
}
