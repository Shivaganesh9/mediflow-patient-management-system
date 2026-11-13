package com.mediflow.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRolesRequest {

    @NotEmpty(message = "At least one role is required")
    private Set<@NotBlank(message = "Role name must not be blank") String> roles;
}
