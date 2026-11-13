package com.mediflow.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetResponse {

    private String message;

    /**
     * Only populated when {@code auth.password-reset.expose-token=true} (local/dev/test).
     * In every other environment the token is delivered out-of-band and this stays null.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String token;
}
