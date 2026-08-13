package com.mediflow.patient.repository;

import com.mediflow.patient.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {
    Optional<Patient> findByPatientId(UUID patientId);
    Optional<Patient> findByPatientIdAndDeletedAtIsNull(UUID patientId);
    boolean existsByPatientId(UUID patientId);
    boolean existsByPatientIdAndDeletedAtIsNull(UUID patientId);
    boolean existsByEmailAndDeletedAtIsNull(String email);
    boolean existsByEmailAndPatientIdNotAndDeletedAtIsNull(String email, UUID patientId);

    @Query("select p from Patient p where p.deletedAt is null and "
            + "(:search is null or :search = '' or "
            + "lower(p.firstName) like lower(concat('%', :search, '%')) or "
            + "lower(p.lastName) like lower(concat('%', :search, '%')) or "
            + "lower(p.email) like lower(concat('%', :search, '%')) or "
            + "p.phone like concat('%', :search, '%'))")
    Page<Patient> searchActivePatients(@Param("search") String search, Pageable pageable);
}
