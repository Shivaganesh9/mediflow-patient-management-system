package com.mediflow.auth.seed;

import com.mediflow.auth.entity.Role;
import com.mediflow.auth.entity.User;
import com.mediflow.auth.repository.RoleRepository;
import com.mediflow.auth.repository.UserRepository;
import com.mediflow.common.enums.AccountStatus;
import com.mediflow.common.seed.DevSeedData;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Dev-only seeder (active under the {@code seed} profile) that creates the deterministic
 * {@code admin}/{@code doctor}/{@code patient} accounts with known credentials.
 *
 * <p>The auth user IDs are fixed and intentionally equal the {@code patientId}/{@code doctorId}
 * business keys seeded by the patient- and doctor-services, so frontend list scoping by
 * {@code user.id} works end to end. Idempotent: existing usernames are skipped.</p>
 */
@Component
@Profile("seed")
@RequiredArgsConstructor
public class AuthDevDataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed();
    }

    @Transactional
    public void seed() {
        createUser(DevSeedData.ADMIN_USER_ID, DevSeedData.ADMIN_USERNAME, DevSeedData.ADMIN_EMAIL,
                "System Administrator", "ADMIN", DevSeedData.ADMIN_PASSWORD);
        createUser(DevSeedData.DOCTOR_USER_ID, DevSeedData.DOCTOR_USERNAME, DevSeedData.DOCTOR_EMAIL,
                "Dev Doctor", "DOCTOR", DevSeedData.DOCTOR_PASSWORD);
        createUser(DevSeedData.PATIENT_USER_ID, DevSeedData.PATIENT_USERNAME, DevSeedData.PATIENT_EMAIL,
                "Dev Patient", "PATIENT", DevSeedData.PATIENT_PASSWORD);
    }

    private void createUser(java.util.UUID userId, String username, String email, String fullName,
                            String roleName, String rawPassword) {
        if (userRepository.existsByUsername(username) || userRepository.existsByEmail(email)) {
            return;
        }
        Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(Role.builder().name(roleName)
                        .description("Seeded " + roleName.toLowerCase() + " role").build()));

        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .fullName(fullName)
                .phone("555-0100")
                .status(AccountStatus.ACTIVE)
                .roles(Set.of(role))
                .build();
        user.setId(userId);
        userRepository.save(user);
    }
}