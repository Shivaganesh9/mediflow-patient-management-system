package com.mediflow.medicalrecord.repository;

import com.mediflow.medicalrecord.entity.MedicalRecord;
import com.mediflow.medicalrecord.entity.MedicalRecordType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, UUID> {

    Optional<MedicalRecord> findByRecordId(UUID recordId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MedicalRecord r where r.recordId = :recordId")
    Optional<MedicalRecord> findByRecordIdForUpdate(@Param("recordId") UUID recordId);

    @Query("select r from MedicalRecord r "
            + "where (:patientId is null or r.patientId = :patientId) "
            + "and (:doctorId is null or r.doctorId = :doctorId) "
            + "and (:recordType is null or r.recordType = :recordType) "
            + "and (:fromDate is null or r.encounterDate >= :fromDate) "
            + "and (:toDate is null or r.encounterDate <= :toDate)")
    Page<MedicalRecord> search(@Param("patientId") UUID patientId,
                               @Param("doctorId") UUID doctorId,
                               @Param("recordType") MedicalRecordType recordType,
                               @Param("fromDate") LocalDate fromDate,
                               @Param("toDate") LocalDate toDate,
                               Pageable pageable);
}
