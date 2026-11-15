package com.mediflow.common.resilience;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * Small per-dependency circuit breaker for low-volume synchronous calls where a thread
 * pool or retry loop would amplify an outage.
 *
 * <p>States: {@code CLOSED} (calls allowed), {@code OPEN} (calls rejected until the cool-down
 * elapses) and {@code HALF_OPEN} (one trial call allowed). A failed trial re-opens the
 * breaker immediately; a successful trial closes it. The implementation is intentionally
 * dependency-free and not shared across instances; each service protects its own calls to
 * a dependency.</p>
 */
public final class CircuitBreaker {

    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    private final int failureThreshold;
    private final long openNanos;
    private final LongSupplier nanoClock;

    private State state = State.CLOSED;
    private int consecutiveFailures;
    private long openUntilNanos;
    private boolean trialInFlight;

    public CircuitBreaker(int failureThreshold, Duration openDuration) {
        this(failureThreshold, openDuration, System::nanoTime);
    }

    CircuitBreaker(int failureThreshold, Duration openDuration, LongSupplier nanoClock) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold must be >= 1");
        }
        this.failureThreshold = failureThreshold;
        this.openNanos = openDuration.toNanos();
        this.nanoClock = nanoClock;
    }

    /** @return true when the caller may attempt the dependency. */
    public synchronized boolean allowRequest() {
        if (state == State.CLOSED) {
            return true;
        }
        if (state == State.OPEN && nanoClock.getAsLong() >= openUntilNanos) {
            state = State.HALF_OPEN;
            trialInFlight = false;
        }
        if (state == State.HALF_OPEN && !trialInFlight) {
            trialInFlight = true;
            return true;
        }
        return false;
    }

    public synchronized void recordSuccess() {
        consecutiveFailures = 0;
        trialInFlight = false;
        state = State.CLOSED;
    }

    public synchronized void recordFailure() {
        trialInFlight = false;
        if (state == State.HALF_OPEN) {
            open();
            return;
        }
        consecutiveFailures++;
        if (consecutiveFailures >= failureThreshold) {
            open();
        }
    }

    public synchronized State state() {
        return state;
    }

    public synchronized int consecutiveFailures() {
        return consecutiveFailures;
    }

    private void open() {
        state = State.OPEN;
        openUntilNanos = nanoClock.getAsLong() + openNanos;
        consecutiveFailures = failureThreshold;
    }
}
