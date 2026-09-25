package com.example.civicgrid.service;

import com.example.civicgrid.entity.ChartOfAccount;
import com.example.civicgrid.entity.Journal;
import com.example.civicgrid.entity.JournalEntry;
import com.example.civicgrid.entity.Zone;
import com.example.civicgrid.repository.JournalEntryRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class JournalEntryService {

    private final JournalEntryRepository journalEntryRepository;

    @Getter
    @Builder
    public static class EntryLine {
        private ChartOfAccount account;
        private BigDecimal debit;
        private BigDecimal credit;
        private String description;
        private Zone zone;
    }

    /**
     * Records a balanced double-entry transaction.
     * Enforces the fundamental accounting equation: sum(Debits) == sum(Credits).
     */
    public List<JournalEntry> recordTransaction(
            String entryNumber,
            LocalDate date,
            Journal journal,
            String reference,
            List<EntryLine> lines
    ) {
        if (lines == null || lines.size() < 2) {
            throw new IllegalArgumentException("A double-entry transaction requires at least 2 lines (debit and credit).");
        }

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (EntryLine line : lines) {
            BigDecimal debit = line.getDebit() != null ? line.getDebit() : BigDecimal.ZERO;
            BigDecimal credit = line.getCredit() != null ? line.getCredit() : BigDecimal.ZERO;

            if (debit.compareTo(BigDecimal.ZERO) < 0 || credit.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Debit and Credit amounts must be non-negative.");
            }

            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
        }

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new IllegalStateException(String.format(
                    "Unbalanced journal entry '%s'! Total Debit (%s) does not equal Total Credit (%s).",
                    entryNumber, totalDebit, totalCredit
            ));
        }

        List<JournalEntry> createdEntries = new ArrayList<>();
        for (EntryLine line : lines) {
            BigDecimal debit = line.getDebit() != null ? line.getDebit() : BigDecimal.ZERO;
            BigDecimal credit = line.getCredit() != null ? line.getCredit() : BigDecimal.ZERO;

            JournalEntry entry = JournalEntry.builder()
                    .entryNumber(entryNumber)
                    .entryDate(date)
                    .journal(journal)
                    .account(line.getAccount())
                    .debit(debit)
                    .credit(credit)
                    .description(line.getDescription())
                    .reference(reference)
                    .zone(line.getZone())
                    .build();

            createdEntries.add(journalEntryRepository.save(entry));
        }

        log.info("Recorded double-entry transaction '{}' with balanced amount of {}", entryNumber, totalDebit);
        return createdEntries;
    }

    @Transactional(readOnly = true)
    public List<JournalEntry> getByEntryNumber(String entryNumber) {
        return journalEntryRepository.findByEntryNumber(entryNumber);
    }

    @Transactional(readOnly = true)
    public List<JournalEntry> getAll() {
        return journalEntryRepository.findAll();
    }
}
