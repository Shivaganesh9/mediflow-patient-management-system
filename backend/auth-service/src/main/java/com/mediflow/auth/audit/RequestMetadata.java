package com.mediflow.auth.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Best-effort request metadata for security audit records. */
public record RequestMetadata(String ipAddress, String userAgent) {

    private static final int MAX_USER_AGENT = 255;
    private static final int MAX_IP = 64;

    public static RequestMetadata current() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return new RequestMetadata(null, null);
        }
        HttpServletRequest request = attributes.getRequest();
        return new RequestMetadata(truncate(clientIp(request), MAX_IP),
                truncate(request.getHeader("User-Agent"), MAX_USER_AGENT));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
