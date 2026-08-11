package com.mediflow.billing.dto;

import com.mediflow.billing.entity.InvoiceItemType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

@Value
@Builder
public class InvoiceItemResponse {
    UUID itemId;
    InvoiceItemType itemType;
    String description;
    BigDecimal quantity;
    BigDecimal unitPrice;
    BigDecimal amount;
}
