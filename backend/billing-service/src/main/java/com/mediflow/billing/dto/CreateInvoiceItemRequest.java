package com.mediflow.billing.dto;

import com.mediflow.billing.entity.InvoiceItemType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateInvoiceItemRequest {

    private InvoiceItemType itemType;

    @NotBlank
    @Size(max = 500)
    private String description;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal quantity;

    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal unitPrice;
}
