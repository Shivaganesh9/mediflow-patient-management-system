package com.mediflow.common.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class DateTimeUtil {
    private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    private DateTimeUtil() {
    }

    public static String toIsoUtc(Instant instant) {
        return ISO_UTC.format(instant);
    }
}
