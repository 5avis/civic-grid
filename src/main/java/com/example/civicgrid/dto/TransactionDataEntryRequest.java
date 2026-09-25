package com.example.civicgrid.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDataEntryRequest {

    /**
     * Transaction type: VENDOR_BILL, CUSTOMER_INVOICE, or PAYMENT
     */
    @NotBlank(message = "Transaction type is required (VENDOR_BILL, CUSTOMER_INVOICE, PAYMENT).")
    private String type;

    @NotNull(message = "Amount is required.")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero.")
    private BigDecimal amount;

    private LocalDate date;

    /**
     * Contact ID (vendor or customer). If null, partyName can be used.
     */
    private Long partyId;

    /**
     * Name of the vendor or customer (e.g., 'Electric Utility', 'Repair Contractor', 'City Municipality')
     */
    private String partyName;

    /**
     * Chart of Account Code (e.g., '4010' for Electricity Expense, '3010' for Service Fee, '1010' for Bank)
     */
    @NotBlank(message = "Account code is required.")
    private String accountCode;

    /**
     * Optional Zone ID for linking expenses to zone budgets
     */
    private Long zoneId;

    /**
     * Optional Product ID
     */
    private Long productId;

    /**
     * Optional description or notes
     */
    private String description;
}
