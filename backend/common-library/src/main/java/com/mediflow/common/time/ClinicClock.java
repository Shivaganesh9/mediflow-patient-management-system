package com.mediflow.common.time;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Clinic-wide clock used for business-time decisions (past slots, encounter dates, audit stamps).
 *
 * <p>Appointment times, availability windows and encounter dates are stored as local values. The
 * configured clinic zone ({@code clinic.time-zone}, default {@value #DEFAULT_ZONE}) defines what
 * "now" and "today" mean for every service, so a booking is judged consistently no matter which
 * zone the server or browser runs in. Keep the relevant services on the same value in a
 * deployment.</p>
 */
@Component
public class ClinicClock {

    public static final String DEFAULT_ZONE = "UTC";

    private final Clock clock;

    @Autowired
    public ClinicClock(@Value("${clinic.time-zone:" + DEFAULT_ZONE + "}") String timeZone) {
        this(Clock.system(parseZone(timeZone)));
    }

    /** Test seam: lets callers supply a fixed clock without touching the system zone. */
    public ClinicClock(Clock clock) {
        this.clock = clock;
    }

    public Instant instant() {
        return clock.instant();
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public ZoneId zone() {
        return clock.getZone();
    }

    private static ZoneId parseZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return ZoneId.of(DEFAULT_ZONE);
        }
        return ZoneId.of(timeZone.trim());
    }
}
