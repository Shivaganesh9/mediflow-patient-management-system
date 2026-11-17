package com.mediflow.billing.service;

import com.mediflow.billing.repository.PaymentIdempotencyRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Prunes payment idempotency claims after their retention window.
 *
 * <p>A key only has to be remembered as long as a client might still retry the same request.
 * After {@code billing.payment-idempotency.retention} (default 7 days) the claim is deleted, so the
 * table stays small and a recycled key cannot resurrect an old result. Deletion never touches the
 * payment it referenced: the financial record is permanent.</p>
 */
@Component
@ConditionalOnProperty(name = "billing.payment-idempotency.cleanup-enabled", havingValue = "true",
        matchIfMissing = true)
@Slf4j
public class PaymentIdempotencyCleanup {

    private final PaymentIdempotencyRepository idempotencyRepository;
    private final Duration retention;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public PaymentIdempotencyCleanup(PaymentIdempotencyRepository idempotencyRepository,
                                     @Value("${billing.payment-idempotency.retention:P7D}") Duration retention,
                                     ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.idempotencyRepository = idempotencyRepository;
        this.retention = retention;
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @Scheduled(cron = "${billing.payment-idempotency.cleanup-cron:0 15 3 * * *}")
    @Transactional
    public void purgeExpiredKeys() {
        Instant cutoff = Instant.now().minus(retention);
        long deleted = idempotencyRepository.deleteCreatedBefore(cutoff);
        if (deleted == 0) {
            log.debug("No payment idempotency keys older than {} to prune", cutoff);
            return;
        }
        log.info("Pruned {} payment idempotency key(s) older than {}", deleted, cutoff);
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            registry.counter("mediflow.billing.payment.idempotency.pruned").increment(deleted);
        }
    }
}
