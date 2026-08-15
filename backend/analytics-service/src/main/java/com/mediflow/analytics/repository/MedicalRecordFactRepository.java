package com.mediflow.analytics.repository;

import com.mediflow.analytics.entity.MedicalRecordFact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MedicalRecordFactRepository extends JpaRepository<MedicalRecordFact, UUID> {

    Optional<MedicalRecordFact> findByRecordId(UUID recordId);

    @Query(value = "SELECT COUNT(*) FROM (SELECT patient_id FROM medical_record_facts " +
            "GROUP BY patient_id HAVING MIN(encounter_date) BETWEEN :from AND :to) t",
            nativeQuery = true)
    long countNewPatientsBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}