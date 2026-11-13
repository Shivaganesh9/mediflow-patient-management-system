package com.mediflow.doctor.service;

import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.doctor.dto.CreateDoctorRequest;
import com.mediflow.doctor.dto.DoctorResponse;
import com.mediflow.doctor.entity.Doctor;
import com.mediflow.doctor.identity.IdentityVerifier;
import com.mediflow.doctor.mapper.DoctorMapper;
import com.mediflow.doctor.repository.DoctorRepository;
import com.mediflow.doctor.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final DoctorMapper doctorMapper;
    private final CurrentUser currentUser;
    private final IdentityVerifier identityVerifier;

    @Transactional
    public Doctor createDoctor(CreateDoctorRequest request) {
        if (doctorRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("A doctor with this email already exists");
        }

        UUID doctorId = resolveDoctorIdentity(request.getUserId());
        if (doctorRepository.existsByDoctorId(doctorId)) {
            throw new IllegalArgumentException("A doctor profile already exists for this user");
        }

        Doctor doctor = doctorMapper.toEntity(request);
        doctor.setDoctorId(doctorId);
        doctor.setCreatedBy(currentUser.currentUsername());
        doctor = doctorRepository.save(doctor);
        log.info("Created doctor with id {}", doctor.getDoctorId());
        return doctor;
    }

    public Page<DoctorResponse> searchDoctors(String search, String department, Pageable pageable) {
        return doctorRepository.search(search, department, pageable).map(doctorMapper::toResponse);
    }

    public Doctor getDoctor(UUID doctorId) {
        Doctor doctor = doctorRepository.findByDoctorId(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found with id: " + doctorId));
        assertDoctorProfileAccess(doctorId);
        return doctor;
    }

    @Transactional
    public Doctor updateDoctor(UUID doctorId, CreateDoctorRequest request) {
        Doctor doctor = getDoctor(doctorId);
        if (doctorRepository.existsByEmailAndDoctorIdNot(request.getEmail(), doctorId)) {
            throw new IllegalArgumentException("A doctor with this email already exists");
        }
        doctorMapper.updateEntity(doctor, request);
        Doctor updatedDoctor = doctorRepository.save(doctor);
        log.info("Updated doctor with id {}", doctorId);
        return updatedDoctor;
    }

    /**
     * Resolves the Auth user identity to be assigned as the doctor business id.
     * <p>A doctor may only create their own profile: the {@code uid} claim of their signed
     * token already proves both the identity and the {@code DOCTOR} role, so no remote call
     * is needed. Every other role must explicitly identify the target Auth user, and that
     * reference is verified against auth-service before the profile is created.</p>
     */
    private UUID resolveDoctorIdentity(UUID requestedUserId) {
        if (currentUser.hasRole("DOCTOR")) {
            UUID selfId = currentUser.requireCurrentUserId();
            if (requestedUserId != null && !requestedUserId.equals(selfId)) {
                throw new AccessDeniedException("Doctors can only create their own doctor profile");
            }
            return selfId;
        }
        if (requestedUserId == null) {
            throw new IllegalArgumentException("userId is required to associate the doctor profile with an Auth user");
        }
        identityVerifier.verifyIdentity(requestedUserId, "DOCTOR");
        return requestedUserId;
    }

    /**
     * Enforces profile ownership for doctors: a doctor may only access their
     * own profile (JWT uid == doctorId). Admin and staff roles retain access.
     * Doctor discovery/search is unaffected because it never reads a specific
     * profile by id.
     */
    private void assertDoctorProfileAccess(UUID doctorId) {
        if (currentUser.hasRole("DOCTOR")) {
            UUID selfId = currentUser.requireCurrentUserId();
            if (!doctorId.equals(selfId)) {
                throw new AccessDeniedException("Doctors can only access their own profile");
            }
        }
    }
}