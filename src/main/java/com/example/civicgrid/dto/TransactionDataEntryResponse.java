package com.example.civicgrid.dto;

import com.example.civicgrid.entity.JournalEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDataEntryResponse {

    private String message;
    private String transactionType;
    private String documentNumber;
    private BigDecimal amount;
    private LocalDate transactionDate;
    private String partyName;
    private String journalEntryNumber;
    private List<JournalEntryLineSummary> journalLines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JournalEntryLineSummary {
        private String accountCode;
        private String accountName;
        private BigDecimal debit;
        private BigDecimal credit;
    }
}
