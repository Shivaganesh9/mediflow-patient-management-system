package com.mediflow.medicalrecord.repository;

import com.mediflow.medicalrecord.entity.MedicalRecordHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MedicalRecordHistoryRepository extends JpaRepository<MedicalRecordHistory, UUID> {

    List<MedicalRecordHistory> findByRecordIdOrderByOccurredAtAscIdAsc(UUID recordId);
}
