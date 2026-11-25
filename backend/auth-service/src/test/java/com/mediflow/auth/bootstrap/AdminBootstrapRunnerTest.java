package com.mediflow.auth.bootstrap;

import com.mediflow.auth.audit.AuthAuditService;
import com.mediflow.auth.audit.AuthEventTypes;
import com.mediflow.auth.config.AuthProperties;
import com.mediflow.auth.entity.Role;
import com.mediflow.auth.entity.User;
import com.mediflow.auth.repository.RoleRepository;
import com.mediflow.auth.repository.UserRepository;
import com.mediflow.auth.service.PasswordPolicy;
import com.mediflow.common.enums.AccountStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthAuditService auditService;

    private AuthProperties properties;
    private AdminBootstrapRunner runner;

    @BeforeEach
    void setUp() {
        properties = new AuthProperties();
        runner = new AdminBootstrapRunner(properties, userRepository, roleRepository,
                passwordEncoder, new PasswordPolicy(properties), auditService);
    }

    @Test
    void doesNothingWhenDisabled() {
        runner.run(null);

        verifyNoInteractions(userRepository, roleRepository, passwordEncoder, auditService);
    }

    @Test
    void neverOverwritesAnExistingAdministrator() {
        enable("rootadmin", "root@example.com", "Str0ngPass!");

        when(userRepository.existsByRoleName("ADMIN")).thenReturn(true);

        runner.run(null);

        verify(userRepository).existsByRoleName("ADMIN");
        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(roleRepository, passwordEncoder, auditService);
    }

    @Test
    void createsAnActiveAdministratorAndAuditsIt() {
        enable("rootadmin", "root@example.com", "Str0ngPass!");
        Role adminRole = Role.builder().name("ADMIN").description("System administrator").build();

        when(userRepository.existsByRoleName("ADMIN")).thenReturn(false);
        when(userRepository.existsByUsername("rootadmin")).thenReturn(false);
        when(userRepository.existsByEmail("root@example.com")).thenReturn(false);
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(passwordEncoder.encode("Str0ngPass!")).thenReturn("encoded-hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        runner.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User created = captor.getValue();

        assertThat(created.getUsername()).isEqualTo("rootadmin");
        assertThat(created.getEmail()).isEqualTo("root@example.com");
        assertThat(created.getPassword()).isEqualTo("encoded-hash");
        assertThat(created.getFullName()).isEqualTo("Initial Administrator");
        assertThat(created.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(created.getRoles()).extracting(Role::getName).containsExactly("ADMIN");
        assertThat(created.getCreatedBy()).isEqualTo("bootstrap");

        verify(roleRepository, never()).save(any());
        verify(auditService).record(eq(AuthEventTypes.ADMIN_BOOTSTRAPPED), eq("SUCCESS"),
                eq(created.getId()), eq("rootadmin"), anyString());
    }

    @Test
    void reusesTheExistingAdminRoleRow() {
        enable("rootadmin", "root@example.com", "Str0ngPass!");
        Role adminRole = Role.builder().name("ADMIN").build();

        when(userRepository.existsByRoleName("ADMIN")).thenReturn(false);
        when(userRepository.existsByUsername("rootadmin")).thenReturn(false);
        when(userRepository.existsByEmail("root@example.com")).thenReturn(false);
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        runner.run(null);

        verify(roleRepository, never()).save(any());
    }

    @Test
    void rejectsAWeakPasswordBeforeCreatingAnything() {
        // Long enough, but letters only: the policy requires letters and digits.
        enable("rootadmin", "root@example.com", "abcdefgh");

        when(userRepository.existsByRoleName("ADMIN")).thenReturn(false);

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("letters and digits");

        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void rejectsATooShortPasswordBeforeCreatingAnything() {
        enable("rootadmin", "root@example.com", "a1");

        when(userRepository.existsByRoleName("ADMIN")).thenReturn(false);

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 8 characters");

        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void rejectsIncompleteConfiguration() {
        properties.getBootstrap().setEnabled(true);
        properties.getBootstrap().setUsername("rootadmin");
        properties.getBootstrap().setEmail("root@example.com");

        when(userRepository.existsByRoleName("ADMIN")).thenReturn(false);

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_PASSWORD");

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAnInvalidEmail() {
        enable("rootadmin", "not-an-email", "Str0ngPass!");

        when(userRepository.existsByRoleName("ADMIN")).thenReturn(false);

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("valid email");

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void refusesToReuseAnExistingEmail() {
        enable("rootadmin", "root@example.com", "Str0ngPass!");

        when(userRepository.existsByRoleName("ADMIN")).thenReturn(false);
        when(userRepository.existsByUsername("rootadmin")).thenReturn(false);
        when(userRepository.existsByEmail("root@example.com")).thenReturn(true);

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("email");

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void refusesToReuseAnExistingUsername() {
        enable("rootadmin", "root@example.com", "Str0ngPass!");

        when(userRepository.existsByRoleName("ADMIN")).thenReturn(false);
        when(userRepository.existsByUsername("rootadmin")).thenReturn(true);

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("username");

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void treatsAConcurrentBootstrapWinAsSuccess() {
        enable("rootadmin", "root@example.com", "Str0ngPass!");
        Role adminRole = Role.builder().name("ADMIN").build();

        when(userRepository.existsByRoleName("ADMIN")).thenReturn(false, true);
        when(userRepository.existsByUsername("rootadmin")).thenReturn(false);
        when(userRepository.existsByEmail("root@example.com")).thenReturn(false);
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-hash");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate username"));

        runner.run(null);

        verify(auditService, never()).record(anyString(), anyString(), any(), anyString(), anyString());
    }

    private void enable(String username, String email, String password) {
        AuthProperties.Bootstrap bootstrap = properties.getBootstrap();
        bootstrap.setEnabled(true);
        bootstrap.setUsername(username);
        bootstrap.setEmail(email);
        bootstrap.setPassword(password);
    }
}
