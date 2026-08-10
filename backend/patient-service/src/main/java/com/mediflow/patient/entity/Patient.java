package com.mediflow.patient.entity;

import com.mediflow.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Access(AccessType.FIELD)
@Table(name = "patients")
@Getter
@Setter
@NoArgsConstructor
public class Patient extends BaseEntity {

    @Column(nullable = false, unique = true, updatable = false)
    private UUID patientId = UUID.randomUUID();

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String phone;

    private LocalDate dateOfBirth;

    @Column(length = 1000)
    private String address;

    @Column(length = 1000)
    private String medicalHistory;

    @Column(name = "emergency_contact_name", length = 255)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 50)
    private String emergencyContactPhone;

    @Column(name = "insurance_provider", length = 255)
    private String insuranceProvider;

    @Column(name = "insurance_member_id", length = 100)
    private String insuranceMemberId;

    @Version
    private Long version;
}
