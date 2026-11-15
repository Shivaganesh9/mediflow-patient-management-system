package com.mediflow.appointment.event.outbox;

/**
 * Lifecycle of a domain event stored in the transactional outbox.
 *
 * <p>{@code PENDING} rows are retried until the broker acknowledges them ({@code PUBLISHED}) or
 * the retry budget is exhausted ({@code DEAD}). A dead row is never discarded automatically so an
 * operator can inspect and replay it.</p>
 */
public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    DEAD
}
