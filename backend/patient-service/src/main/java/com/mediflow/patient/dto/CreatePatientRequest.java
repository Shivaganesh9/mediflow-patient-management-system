package com.mediflow.patient.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreatePatientRequest {
    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String phone;

    @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$", message = "Date of birth must use yyyy-MM-dd format")
    private String dateOfBirth;
    private String address;
    private String medicalHistory;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String insuranceProvider;
    private String insuranceMemberId;
}
