package com.mediflow.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateInvoiceRequest {

    @NotNull
    private UUID patientId;

    @NotNull
    private Double amount;

    @NotBlank
    private String currency;

    @NotBlank
    private String description;
}
