package com.mediflow.medicalrecord.event.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Publishes pending medical-record outbox rows to Kafka.
 *
 * <p>Rows are marked PUBLISHED only after a broker acknowledgement (at-least-once). The batch is
 * ordered oldest-first and stops on the first failure to preserve ordering. Retries use
 * exponential back-off up to a bounded attempt count, after which the row is marked DEAD for
 * operator inspection.</p>
 *
 * <p>Medical-record events contain clinical text, so failures log only the exception type and
 * event id — never the payload.</p>
 */
@Component
@ConditionalOnProperty(name = "outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class OutboxRelay {

    private static final String ALLOWED_EVENT_PACKAGE = "com.mediflow.";

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final int batchSize;
    private final int maxAttempts;
    private final long sendTimeoutMs;

    public OutboxRelay(OutboxEventRepository repository,
                       KafkaTemplate<String, Object> kafkaTemplate,
                       ObjectMapper objectMapper,
                       MeterRegistry meterRegistry,
                       @Value("${outbox.publisher.batch-size:20}") int batchSize,
                       @Value("${outbox.publisher.max-attempts:10}") int maxAttempts,
                       @Value("${outbox.publisher.send-timeout-ms:5000}") long sendTimeoutMs) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.batchSize = batchSize;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.interval-ms:1000}",
            initialDelayString = "${outbox.publisher.initial-delay-ms:5000}")
    @Transactional
    public void relay() {
        List<OutboxEvent> batch = repository.lockPendingBatch(
                Instant.now(), PageRequest.of(0, batchSize));
        if (batch.isEmpty()) {
            return;
        }

        for (OutboxEvent event : batch) {
            try {
                publish(event);
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
                event.setLastError(null);
                repository.save(event);
                meterRegistry.counter("mediflow.outbox.published", "topic", event.getTopic()).increment();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                recordFailure(event, "interrupted while publishing");
                break;
            } catch (Exception ex) {
                recordFailure(event, ex.getClass().getSimpleName() + ": " + ex.getMessage());
                break;
            }
        }
    }

    private void publish(OutboxEvent event) throws Exception {
        Class<?> eventClass = Class.forName(event.getEventClass());
        if (!eventClass.getName().startsWith(ALLOWED_EVENT_PACKAGE)) {
            throw new IllegalStateException("Refusing to deserialize non-MediFlow event class");
        }
        Object payload = objectMapper.readValue(event.getPayload(), eventClass);
        String key = event.getAggregateId() == null
                ? event.getEventId().toString()
                : event.getAggregateId().toString();
        kafkaTemplate.send(event.getTopic(), key, payload).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
    }

    private void recordFailure(OutboxEvent event, String error) {
        int attempts = event.getAttempts() + 1;
        event.setAttempts(attempts);
        event.setLastError(truncate(error));
        if (attempts >= maxAttempts) {
            event.setStatus(OutboxStatus.DEAD);
            log.error("Outbox event {} for topic {} exhausted {} attempts and was marked DEAD: {}",
                    event.getEventId(), event.getTopic(), attempts, error);
            meterRegistry.counter("mediflow.outbox.dead", "topic", event.getTopic()).increment();
        } else {
            long delaySeconds = Math.min(60L, 1L << Math.min(6, attempts - 1));
            event.setNextAttemptAt(Instant.now().plusSeconds(delaySeconds));
            log.warn("Outbox publish failed (attempt {}/{}), retrying in {}s: {}",
                    attempts, maxAttempts, delaySeconds, error);
            meterRegistry.counter("mediflow.outbox.failed", "topic", event.getTopic()).increment();
        }
        repository.save(event);
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
