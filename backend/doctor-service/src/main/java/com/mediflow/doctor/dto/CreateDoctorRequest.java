package com.mediflow.doctor.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class CreateDoctorRequest {

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String phone;

    @NotBlank
    private String specialty;

    @NotNull
    private Integer yearsOfExperience;

    @NotBlank
    private String department;

    private String qualification;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    private java.math.BigDecimal consultationFee;
}
