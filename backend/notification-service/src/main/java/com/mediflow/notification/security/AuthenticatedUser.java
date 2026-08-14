package com.mediflow.notification.security;

import java.util.List;
import java.util.UUID;

public record AuthenticatedUser(String username, UUID userId, List<String> roles) {
}
