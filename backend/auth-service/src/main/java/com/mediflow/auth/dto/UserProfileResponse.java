package com.mediflow.auth.dto;

import com.mediflow.common.enums.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private UUID id;
    private String username;
    private String email;
    private String fullName;
    private String phone;
    private AccountStatus status;
    private Set<String> roles;
}
