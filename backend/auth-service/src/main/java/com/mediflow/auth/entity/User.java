package com.mediflow.auth.entity;

import com.mediflow.common.entity.BaseEntity;
import com.mediflow.common.enums.AccountStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;

@Entity
@Access(AccessType.FIELD)
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_username", columnNames = "username"),
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    @NotBlank
    private String username;

    @Column(nullable = false, unique = true, length = 255)
    @Email
    @NotBlank
    private String email;

    @Column(nullable = false)
    @NotBlank
    private String password;

    @Column(name = "full_name", length = 150)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AccountStatus status;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<Role> roles = new HashSet<>();
}
