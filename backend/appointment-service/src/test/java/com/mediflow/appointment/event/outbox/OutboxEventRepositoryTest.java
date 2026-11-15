package com.mediflow.appointment.event.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class OutboxEventRepositoryTest {

    @Autowired
    private OutboxEventRepository repository;

    @Test
    void locksOnlyDuePendingEvents() {
        OutboxEvent due = persisted(-60, OutboxStatus.PENDING);
        persisted(60, OutboxStatus.PENDING);
        persisted(-60, OutboxStatus.PUBLISHED);
        persisted(-60, OutboxStatus.DEAD);

        List<OutboxEvent> batch = repository.lockPendingBatch(Instant.now(), PageRequest.of(0, 10));

        assertThat(batch).extracting(OutboxEvent::getEventId).containsExactly(due.getEventId());
    }

    private OutboxEvent persisted(long nextAttemptOffsetSeconds, OutboxStatus status) {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(java.util.UUID.randomUUID());
        event.setAggregateId(java.util.UUID.randomUUID());
        event.setTopic("appointment.created.v1");
        event.setEventType("APPOINTMENT_CREATED");
        event.setEventClass(com.mediflow.appointment.event.AppointmentEvent.class.getName());
        event.setPayload("{}");
        event.setStatus(status);
        event.setAttempts(0);
        event.setNextAttemptAt(Instant.now().plusSeconds(nextAttemptOffsetSeconds));
        return repository.saveAndFlush(event);
    }
}
