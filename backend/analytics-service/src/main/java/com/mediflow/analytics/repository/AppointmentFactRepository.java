package com.mediflow.analytics.repository;

import com.mediflow.analytics.entity.AppointmentFact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppointmentFactRepository extends JpaRepository<AppointmentFact, UUID> {

    Optional<AppointmentFact> findByAppointmentId(UUID appointmentId);

    @Query(value = "SELECT COUNT(*) FROM appointment_facts WHERE appointment_date BETWEEN :from AND :to",
            nativeQuery = true)
    long countAppointmentsBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(value = "SELECT COUNT(*) FROM appointment_facts WHERE appointment_date BETWEEN :from AND :to AND status = :status",
            nativeQuery = true)
    long countByStatusBetween(@Param("from") LocalDate from, @Param("to") LocalDate to,
                              @Param("status") String status);

    @Query(value = "SELECT COUNT(DISTINCT patient_id) FROM appointment_facts WHERE appointment_date BETWEEN :from AND :to",
            nativeQuery = true)
    long countDistinctPatientsBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(value = "SELECT COUNT(DISTINCT doctor_id) FROM appointment_facts WHERE appointment_date BETWEEN :from AND :to AND doctor_id IS NOT NULL",
            nativeQuery = true)
    long countDistinctDoctorsBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(value = "SELECT status, COUNT(*) FROM appointment_facts WHERE appointment_date BETWEEN :from AND :to GROUP BY status",
            nativeQuery = true)
    List<Object[]> countByStatusGroupedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(value = "SELECT appointment_date, COUNT(*) FROM appointment_facts WHERE appointment_date BETWEEN :from AND :to GROUP BY appointment_date ORDER BY appointment_date",
            nativeQuery = true)
    List<Object[]> dailyCountBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(value = "SELECT doctor_id, COUNT(*), COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) FROM appointment_facts " +
            "WHERE appointment_date BETWEEN :from AND :to AND doctor_id IS NOT NULL " +
            "GROUP BY doctor_id ORDER BY COUNT(*) DESC",
            nativeQuery = true)
    List<Object[]> doctorRankingBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(value = "SELECT patient_id, COUNT(*), MIN(appointment_date) FROM appointment_facts " +
            "WHERE appointment_date BETWEEN :from AND :to " +
            "GROUP BY patient_id ORDER BY COUNT(*) DESC LIMIT :limit",
            nativeQuery = true)
    List<Object[]> mostActivePatientsBetween(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                             @Param("limit") int limit);
}