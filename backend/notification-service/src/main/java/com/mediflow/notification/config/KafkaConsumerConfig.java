package com.mediflow.notification.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DeserializationException;

/**
 * Consumer reliability policy for notification events.
 *
 * <ul>
 *   <li>Bounded exponential retries (5 retries after the initial attempt, 1s doubling to 20s) for
 *       transient failures such as a database outage.</li>
 *   <li>Malformed payloads are not retried: they can never succeed and go straight to the DLQ.</li>
 *   <li>Exhausted retries are published to {@code dlq.<topic>}; failure to publish there fails the
 *       record so the offset is not committed and the message is not lost.</li>
 *   <li>Offsets are committed per record only after successful processing
 *       ({@code ack-mode: record}, {@code enable-auto-commit: false}).</li>
 * </ul>
 */
@Configuration
public class KafkaConsumerConfig {

    private static final long INITIAL_INTERVAL_MS = 1000L;
    private static final long MAX_INTERVAL_MS = 20_000L;
    private static final int MAX_RETRIES = 5;

    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaTemplate<String, Object> kafkaTemplate,
                                                                       MeterRegistry meterRegistry) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition("dlq." + record.topic(), record.partition())) {
            @Override
            public void accept(ConsumerRecord<?, ?> record, Exception ex) {
                super.accept(record, ex);
                meterRegistry.counter("mediflow.kafka.consumer.dead-letter", "topic", record.topic())
                        .increment();
            }
        };
        recoverer.setFailIfSendResultIsError(true);
        return recoverer;
    }

    @Bean
    public DefaultErrorHandler notificationErrorHandler(DeadLetterPublishingRecoverer recoverer,
                                                        MeterRegistry meterRegistry) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(MAX_RETRIES);
        backOff.setInitialInterval(INITIAL_INTERVAL_MS);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(MAX_INTERVAL_MS);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(DeserializationException.class);
        handler.setRetryListeners((record, ex, deliveryAttempt) ->
                meterRegistry.counter("mediflow.kafka.consumer.retries", "topic", record.topic())
                        .increment());
        return handler;
    }
}
