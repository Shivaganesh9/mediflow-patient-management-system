package com.mediflow.common.logging;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Request/event correlation identifier used across HTTP and Kafka boundaries.
 *
 * <p>The identifier is stored in the SLF4J {@link MDC} under {@link #MDC_KEY}, so the
 * configured log pattern emits it on every line. Producers copy it into the domain-event
 * envelope and consumers restore it while processing a record, which links the original
 * HTTP request to the asynchronous work it triggered.</p>
 */
public final class CorrelationContext {

    public static final String MDC_KEY = "correlationId";
    public static final String HEADER = "X-Correlation-ID";

    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    private CorrelationContext() {
    }

    /** Current correlation id, or {@code null} outside a request/consumer thread. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    /** Current correlation id as UUID when it is a UUID, otherwise {@code null}. */
    public static UUID currentUuid() {
        return parse(current());
    }

    public static UUID parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Accepts a caller-supplied id only when it is short and free of control characters. */
    public static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return SAFE_VALUE.matcher(trimmed).matches() ? trimmed : null;
    }

    public static void set(String correlationId) {
        if (correlationId == null) {
            MDC.remove(MDC_KEY);
        } else {
            MDC.put(MDC_KEY, correlationId);
        }
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }

    /**
     * Runs work with the given correlation id restored afterwards. Null ids keep whatever
     * value the current thread already has (for listener threads that is normally none).
     */
    public static void run(UUID correlationId, Runnable work) {
        if (correlationId == null) {
            work.run();
            return;
        }
        String previous = current();
        set(correlationId.toString());
        try {
            work.run();
        } finally {
            set(previous);
        }
    }
}
