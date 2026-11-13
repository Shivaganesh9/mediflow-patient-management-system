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
 * accounts for every {@link com.mediflow.common.enums.UserRole}: {@code admin},
 * {@code doctor}, {@code patient}, {@code nurse}, {@code billing} and {@code support}.
 *
 * <p>The auth user IDs are fixed and intentionally equal the {@code patientId}/{@code doctorId}
 * business keys seeded by the patient- and doctor-services, so frontend list scoping by
 * {@code user.id} works end to end. The staff accounts exist so each role in the
 * authorization matrix can be exercised end to end (including negative 403 checks).
 * Idempotent: existing usernames are skipped.</p>
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
        createUser(DevSeedData.NURSE_USER_ID, DevSeedData.NURSE_USERNAME, DevSeedData.NURSE_EMAIL,
                "Dev Nurse", "NURSE", DevSeedData.NURSE_PASSWORD);
        createUser(DevSeedData.BILLING_STAFF_USER_ID, DevSeedData.BILLING_STAFF_USERNAME,
                DevSeedData.BILLING_STAFF_EMAIL, "Dev Billing Staff", "BILLING_STAFF",
                DevSeedData.BILLING_STAFF_PASSWORD);
        createUser(DevSeedData.SUPPORT_USER_ID, DevSeedData.SUPPORT_USERNAME, DevSeedData.SUPPORT_EMAIL,
                "Dev Support", "SUPPORT", DevSeedData.SUPPORT_PASSWORD);
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