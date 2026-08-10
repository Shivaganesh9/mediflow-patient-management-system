package com.mediflow.doctor.entity;

import com.mediflow.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Access(AccessType.FIELD)
@Table(name = "doctors")
@Getter
@Setter
@NoArgsConstructor
public class Doctor extends BaseEntity {

    @Column(nullable = false, unique = true, updatable = false)
    private UUID doctorId = UUID.randomUUID();

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String specialty;

    @Column(nullable = false)
    private Integer yearsOfExperience;

    @Column(nullable = false, length = 255)
    private String department;

    @Column(length = 2000)
    private String qualification;

    @Column(nullable = false)
    private java.math.BigDecimal consultationFee;

    @Version
    private Long version;
}
