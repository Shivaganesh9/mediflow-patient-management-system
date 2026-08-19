package com.mediflow.auth.seed;

import com.mediflow.auth.entity.Role;
import com.mediflow.auth.entity.User;
import com.mediflow.auth.repository.RoleRepository;
import com.mediflow.auth.repository.UserRepository;
import com.mediflow.common.enums.AccountStatus;
import com.mediflow.common.seed.DevSeedData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthDevDataSeederTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthDevDataSeeder seeder;

    @Test
    void seedCreatesAdminDoctorPatientUsers() {
        when(roleRepository.findByName(anyString()))
                .thenAnswer(inv -> Optional.of(Role.builder().name(inv.getArgument(0)).build()));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        seeder.seed();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(3)).save(captor.capture());

        Set<java.util.UUID> ids = captor.getAllValues().stream()
                .map(User::getId)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(ids.contains(DevSeedData.ADMIN_USER_ID));
        assertTrue(ids.contains(DevSeedData.DOCTOR_USER_ID));
        assertTrue(ids.contains(DevSeedData.PATIENT_USER_ID));

        User admin = captor.getAllValues().stream()
                .filter(u -> u.getId().equals(DevSeedData.ADMIN_USER_ID))
                .findFirst()
                .orElseThrow();
        assertEquals(DevSeedData.ADMIN_USERNAME, admin.getUsername());
        assertEquals(DevSeedData.ADMIN_EMAIL, admin.getEmail());
        assertEquals(AccountStatus.ACTIVE, admin.getStatus());
        assertEquals("ADMIN", admin.getRoles().iterator().next().getName());
        verify(passwordEncoder, times(3)).encode(anyString());
    }

    @Test
    void seedIsIdempotentWhenUsernamesExist() {
        when(userRepository.existsByUsername(anyString())).thenReturn(true);

        seeder.seed();
        seeder.seed();

        verify(userRepository, never()).save(any(User.class));
        verify(roleRepository, never()).save(any(Role.class));
    }
}