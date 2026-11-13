package com.mediflow.doctor.service;

import com.mediflow.common.exception.ConflictException;
import com.mediflow.doctor.dto.CreateDoctorRequest;
import com.mediflow.doctor.entity.Doctor;
import com.mediflow.doctor.identity.IdentityVerifier;
import com.mediflow.doctor.mapper.DoctorMapper;
import com.mediflow.doctor.repository.DoctorRepository;
import com.mediflow.doctor.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorServiceTest {

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private DoctorMapper doctorMapper;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private IdentityVerifier identityVerifier;

    @InjectMocks
    private DoctorService doctorService;

    @Test
    void createDoctorByAdminRequiresExplicitTargetUser() {
        when(doctorRepository.existsByEmail(any())).thenReturn(false);
        when(currentUser.hasRole("DOCTOR")).thenReturn(false);

        CreateDoctorRequest request = new CreateDoctorRequest();
        request.setEmail("jane.doe@example.com");

        assertThrows(IllegalArgumentException.class, () -> doctorService.createDoctor(request));
        verify(doctorRepository, never()).save(any(Doctor.class));
    }

    @Test
    void createDoctorByAdminUsesExplicitTargetUserAsDoctorId() {
        UUID targetUserId = UUID.randomUUID();
        when(doctorRepository.existsByEmail(any())).thenReturn(false);
        when(currentUser.hasRole("DOCTOR")).thenReturn(false);
        when(currentUser.currentUsername()).thenReturn("admin");
        when(doctorRepository.existsByDoctorId(targetUserId)).thenReturn(false);
        when(doctorMapper.toEntity(any())).thenAnswer(invocation -> new Doctor());
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateDoctorRequest request = new CreateDoctorRequest();
        request.setEmail("jane.doe@example.com");
        request.setUserId(targetUserId);

        Doctor result = doctorService.createDoctor(request);

        assertEquals(targetUserId, result.getDoctorId());
    }

    @Test
    void createDoctorByAdminVerifiesReferencedIdentityAgainstAuthService() {
        UUID targetUserId = UUID.randomUUID();
        when(doctorRepository.existsByEmail(any())).thenReturn(false);
        when(currentUser.hasRole("DOCTOR")).thenReturn(false);
        when(currentUser.currentUsername()).thenReturn("admin");
        when(doctorRepository.existsByDoctorId(targetUserId)).thenReturn(false);
        when(doctorMapper.toEntity(any())).thenAnswer(invocation -> new Doctor());
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateDoctorRequest request = new CreateDoctorRequest();
        request.setEmail("jane.doe@example.com");
        request.setUserId(targetUserId);

        doctorService.createDoctor(request);

        verify(identityVerifier).verifyIdentity(targetUserId, "DOCTOR");
    }

    @Test
    void createDoctorByDoctorSkipsRemoteIdentityVerification() {
        UUID uid = UUID.randomUUID();
        when(doctorRepository.existsByEmail(any())).thenReturn(false);
        when(currentUser.hasRole("DOCTOR")).thenReturn(true);
        when(currentUser.requireCurrentUserId()).thenReturn(uid);
        when(currentUser.currentUsername()).thenReturn("dr-jane");
        when(doctorRepository.existsByDoctorId(uid)).thenReturn(false);
        when(doctorMapper.toEntity(any())).thenAnswer(invocation -> new Doctor());
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateDoctorRequest request = new CreateDoctorRequest();
        request.setEmail("jane.doe@example.com");

        doctorService.createDoctor(request);

        verify(identityVerifier, never()).verifyIdentity(any(), any());
    }

    @Test
    void createDoctorRejectsIdentityThatIsNotActive() {
        UUID targetUserId = UUID.randomUUID();
        when(doctorRepository.existsByEmail(any())).thenReturn(false);
        when(currentUser.hasRole("DOCTOR")).thenReturn(false);
        doThrow(new ConflictException("The referenced user account is not active"))
                .when(identityVerifier).verifyIdentity(targetUserId, "DOCTOR");

        CreateDoctorRequest request = new CreateDoctorRequest();
        request.setEmail("jane.doe@example.com");
        request.setUserId(targetUserId);

        assertThrows(ConflictException.class, () -> doctorService.createDoctor(request));
        verify(doctorRepository, never()).save(any(Doctor.class));
    }

    @Test
    void createDoctorRejectsIdentityWithWrongRole() {
        UUID targetUserId = UUID.randomUUID();
        when(doctorRepository.existsByEmail(any())).thenReturn(false);
        when(currentUser.hasRole("DOCTOR")).thenReturn(false);
        doThrow(new IllegalArgumentException("userId does not reference a DOCTOR identity"))
                .when(identityVerifier).verifyIdentity(targetUserId, "DOCTOR");

        CreateDoctorRequest request = new CreateDoctorRequest();
        request.setEmail("jane.doe@example.com");
        request.setUserId(targetUserId);

        assertThrows(IllegalArgumentException.class, () -> doctorService.createDoctor(request));
        verify(doctorRepository, never()).save(any(Doctor.class));
    }

    @Test
    void createDoctorByDoctorUsesOwnUidAsDoctorId() {
        UUID uid = UUID.randomUUID();
        when(doctorRepository.existsByEmail(any())).thenReturn(false);
        when(currentUser.hasRole("DOCTOR")).thenReturn(true);
        when(currentUser.requireCurrentUserId()).thenReturn(uid);
        when(currentUser.currentUsername()).thenReturn("dr-jane");
        when(doctorRepository.existsByDoctorId(uid)).thenReturn(false);
        when(doctorMapper.toEntity(any())).thenAnswer(invocation -> new Doctor());
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateDoctorRequest request = new CreateDoctorRequest();
        request.setEmail("jane.doe@example.com");

        Doctor result = doctorService.createDoctor(request);

        assertEquals(uid, result.getDoctorId());
    }

    @Test
    void createDoctorByDoctorRejectsForcingAnotherUsersProfile() {
        when(doctorRepository.existsByEmail(any())).thenReturn(false);
        when(currentUser.hasRole("DOCTOR")).thenReturn(true);
        when(currentUser.requireCurrentUserId()).thenReturn(UUID.randomUUID());

        CreateDoctorRequest request = new CreateDoctorRequest();
        request.setEmail("jane.doe@example.com");
        request.setUserId(UUID.randomUUID());

        assertThrows(AccessDeniedException.class, () -> doctorService.createDoctor(request));
        verify(doctorRepository, never()).save(any(Doctor.class));
    }

    @Test
    void createDoctorRejectsDuplicateDoctorIdentity() {
        UUID targetUserId = UUID.randomUUID();
        when(doctorRepository.existsByEmail(any())).thenReturn(false);
        when(currentUser.hasRole("DOCTOR")).thenReturn(false);
        when(doctorRepository.existsByDoctorId(targetUserId)).thenReturn(true);

        CreateDoctorRequest request = new CreateDoctorRequest();
        request.setEmail("jane.doe@example.com");
        request.setUserId(targetUserId);

        assertThrows(IllegalArgumentException.class, () -> doctorService.createDoctor(request));
        verify(doctorRepository, never()).save(any(Doctor.class));
    }

    @Test
    void getDoctorRejectsAnotherDoctorsProfile() {
        UUID doctorId = UUID.randomUUID();
        when(doctorRepository.findByDoctorId(doctorId)).thenReturn(Optional.of(new Doctor()));
        when(currentUser.hasRole("DOCTOR")).thenReturn(true);
        when(currentUser.requireCurrentUserId()).thenReturn(UUID.randomUUID());

        assertThrows(AccessDeniedException.class, () -> doctorService.getDoctor(doctorId));
    }

    @Test
    void getDoctorAllowsDoctorToReadOwnProfile() {
        UUID uid = UUID.randomUUID();
        Doctor doctor = new Doctor();
        when(doctorRepository.findByDoctorId(uid)).thenReturn(Optional.of(doctor));
        when(currentUser.hasRole("DOCTOR")).thenReturn(true);
        when(currentUser.requireCurrentUserId()).thenReturn(uid);

        assertEquals(doctor, doctorService.getDoctor(uid));
    }

    @Test
    void getDoctorAllowsAdminAccess() {
        UUID doctorId = UUID.randomUUID();
        Doctor doctor = new Doctor();
        when(doctorRepository.findByDoctorId(doctorId)).thenReturn(Optional.of(doctor));
        when(currentUser.hasRole("DOCTOR")).thenReturn(false);

        assertEquals(doctor, doctorService.getDoctor(doctorId));
    }

    @Test
    void updateDoctorRejectsAnotherDoctorsProfile() {
        UUID doctorId = UUID.randomUUID();
        when(doctorRepository.findByDoctorId(doctorId)).thenReturn(Optional.of(new Doctor()));
        when(currentUser.hasRole("DOCTOR")).thenReturn(true);
        when(currentUser.requireCurrentUserId()).thenReturn(UUID.randomUUID());

        assertThrows(AccessDeniedException.class,
                () -> doctorService.updateDoctor(doctorId, doctorRequest("jane.doe@example.com")));
    }

    @Test
    void updateDoctorAllowsDoctorToUpdateOwnProfile() {
        UUID uid = UUID.randomUUID();
        Doctor doctor = new Doctor();
        doctor.setDoctorId(uid);
        when(doctorRepository.findByDoctorId(uid)).thenReturn(Optional.of(doctor));
        when(currentUser.hasRole("DOCTOR")).thenReturn(true);
        when(currentUser.requireCurrentUserId()).thenReturn(uid);
        when(doctorRepository.existsByEmailAndDoctorIdNot(any(), eq(uid))).thenReturn(false);
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Doctor result = doctorService.updateDoctor(uid, doctorRequest("jane.doe@example.com"));

        assertEquals(uid, result.getDoctorId());
        verify(doctorRepository).save(any(Doctor.class));
    }

    @Test
    void shouldCreateDoctor() {
        UUID targetUserId = UUID.randomUUID();
        CreateDoctorRequest request = new CreateDoctorRequest();
        request.setFirstName("Jane");
        request.setLastName("Doe");
        request.setEmail("jane.doe@example.com");
        request.setPhone("555-0100");
        request.setSpecialty("Cardiology");
        request.setYearsOfExperience(12);
        request.setUserId(targetUserId);

        Doctor savedDoctor = new Doctor();
        savedDoctor.setFirstName(request.getFirstName());
        savedDoctor.setLastName(request.getLastName());
        savedDoctor.setEmail(request.getEmail());
        savedDoctor.setPhone(request.getPhone());
        savedDoctor.setSpecialty(request.getSpecialty());
        savedDoctor.setYearsOfExperience(request.getYearsOfExperience());
        savedDoctor.setDoctorId(targetUserId);

        when(doctorRepository.existsByEmail(any())).thenReturn(false);
        when(currentUser.hasRole("DOCTOR")).thenReturn(false);
        when(currentUser.currentUsername()).thenReturn("admin");
        when(doctorRepository.existsByDoctorId(targetUserId)).thenReturn(false);
        when(doctorRepository.save(any(Doctor.class))).thenReturn(savedDoctor);
        when(doctorMapper.toEntity(any())).thenReturn(savedDoctor);

        Doctor result = doctorService.createDoctor(request);

        assertNotNull(result);
        assertEquals("Jane", result.getFirstName());
        assertEquals("Cardiology", result.getSpecialty());
        verify(doctorRepository).save(any(Doctor.class));
    }

    private CreateDoctorRequest doctorRequest(String email) {
        CreateDoctorRequest request = new CreateDoctorRequest();
        request.setFirstName("Jane");
        request.setLastName("Doe");
        request.setEmail(email);
        request.setPhone("555-0100");
        request.setSpecialty("Cardiology");
        request.setYearsOfExperience(12);
        request.setDepartment("Cardiology");
        request.setConsultationFee(new java.math.BigDecimal("150.00"));
        return request;
    }
}