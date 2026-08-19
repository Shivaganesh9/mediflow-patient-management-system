package com.mediflow.doctor.repository;

import com.mediflow.doctor.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, UUID> {
    Optional<Doctor> findByDoctorId(UUID doctorId);
    boolean existsByDoctorId(UUID doctorId);
    boolean existsByEmail(String email);
    boolean existsByEmailAndDoctorIdNot(String email, UUID doctorId);

    @Query("select d from Doctor d where (:search is null or :search = '' or "
            + "lower(d.firstName) like lower(concat('%', :search, '%')) or "
            + "lower(d.lastName) like lower(concat('%', :search, '%')) or "
            + "lower(d.email) like lower(concat('%', :search, '%')) or "
            + "lower(d.specialty) like lower(concat('%', :search, '%'))) "
            + "and (:department is null or lower(d.department) = lower(cast(:department as string)))")
    Page<Doctor> search(@Param("search") String search, @Param("department") String department, Pageable pageable);
}
