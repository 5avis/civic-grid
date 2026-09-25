package com.example.civicgrid.repository;

import com.example.civicgrid.entity.AccountType;
import com.example.civicgrid.entity.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    List<JournalEntry> findByEntryNumber(String entryNumber);

    List<JournalEntry> findByJournalCode(String journalCode);

    List<JournalEntry> findByAccountType(AccountType type);

    @Query("SELECT COALESCE(SUM(je.debit), 0) FROM JournalEntry je WHERE je.account.id = :accountId")
    BigDecimal sumDebitByAccountId(@Param("accountId") Long accountId);

    @Query("SELECT COALESCE(SUM(je.credit), 0) FROM JournalEntry je WHERE je.account.id = :accountId")
    BigDecimal sumCreditByAccountId(@Param("accountId") Long accountId);

    @Query("SELECT COALESCE(SUM(je.debit - je.credit), 0) FROM JournalEntry je WHERE je.account.type = :type")
    BigDecimal sumNetByAccountType(@Param("type") AccountType type);
}
