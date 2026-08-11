package com.mediflow.billing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class CreateInvoiceRequest {

    @NotNull
    private UUID patientId;

    private UUID appointmentId;

    @NotBlank
    private String currency;

    private LocalDate dueDate;

    @DecimalMin(value = "0.00")
    private BigDecimal taxRate = BigDecimal.ZERO;

    @DecimalMin(value = "0.00")
    private BigDecimal discount = BigDecimal.ZERO;

    @Size(max = 1000)
    private String notes;

    @Valid
    @NotEmpty
    private List<CreateInvoiceItemRequest> items;
}
