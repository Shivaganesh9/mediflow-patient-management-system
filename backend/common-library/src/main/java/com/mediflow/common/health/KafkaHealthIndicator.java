package com.mediflow.common.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Read-only Kafka broker health indicator.
 *
 * <p>Spring Kafka does not ship a health indicator, so {@code /actuator/health} would otherwise
 * report UP while every publish fails. The admin client is created lazily and reused; a single
 * bounded {@code describeCluster} call keeps the probe cheap even when the broker is unreachable.
 * The indicator lives in the default health group only: Kafka is not part of the HTTP readiness
 * probe because producers buffer through the transactional outbox and consumers retry.</p>
 */
@Component
@ConditionalOnClass(name = "org.springframework.kafka.core.KafkaAdmin")
@ConditionalOnProperty(name = "management.health.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaHealthIndicator implements HealthIndicator, DisposableBean {

    private static final long TIMEOUT_SECONDS = 2L;

    private final KafkaAdmin kafkaAdmin;
    private volatile AdminClient adminClient;

    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    @Override
    public Health health() {
        try {
            String clusterId = adminClient().describeCluster()
                    .clusterId()
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return Health.up().withDetail("clusterId", clusterId).build();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Health.down().withDetail("error", "interrupted").build();
        } catch (Exception ex) {
            return Health.down().withDetail("error", ex.getClass().getSimpleName()).build();
        }
    }

    private AdminClient adminClient() {
        AdminClient client = this.adminClient;
        if (client == null) {
            synchronized (this) {
                client = this.adminClient;
                if (client == null) {
                    client = AdminClient.create(kafkaAdmin.getConfigurationProperties());
                    this.adminClient = client;
                }
            }
        }
        return client;
    }

    @Override
    public void destroy() {
        AdminClient client = this.adminClient;
        if (client != null) {
            client.close(java.time.Duration.ofSeconds(1));
        }
    }
}
