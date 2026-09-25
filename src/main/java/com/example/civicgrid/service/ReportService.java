package com.example.civicgrid.service;

import com.example.civicgrid.entity.AccountType;
import com.example.civicgrid.entity.ChartOfAccount;
import com.example.civicgrid.repository.ChartOfAccountRepository;
import com.example.civicgrid.repository.JournalEntryRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ReportService {

    private final ChartOfAccountRepository chartOfAccountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final BudgetService budgetService;

    // --- Report DTOs ---

    @Getter
    @Builder
    public static class AccountLineDto {
        private String accountCode;
        private String accountName;
        private AccountType accountType;
        private BigDecimal balance;
    }

    @Getter
    @Builder
    public static class BalanceSheetDto {
        private LocalDateTime generatedAt;
        private List<AccountLineDto> assets;
        private BigDecimal totalAssets;
        private List<AccountLineDto> liabilities;
        private BigDecimal totalLiabilities;
        private BigDecimal retainedEarnings; // Net profit/loss from cumulative operations
        private BigDecimal totalLiabilitiesAndEquity;
        private boolean balanced;
    }

    @Getter
    @Builder
    public static class ProfitAndLossDto {
        private LocalDateTime generatedAt;
        private List<AccountLineDto> income;
        private BigDecimal totalIncome;
        private List<AccountLineDto> expenses;
        private BigDecimal totalExpenses;
        private BigDecimal netProfitOrLoss;
    }

    @Getter
    @Builder
    public static class BudgetReportDto {
        private LocalDateTime generatedAt;
        private Integer fiscalYear;
        private List<BudgetService.BudgetComparisonDto> zoneReports;
        private BigDecimal totalPlanned;
        private BigDecimal totalActual;
        private BigDecimal totalVariance;
    }

    /**
     * 1. Balance Sheet — assets (infrastructure, cash) vs liabilities (payables).
     * Generated directly from double-entry journal entries.
     */
    public BalanceSheetDto generateBalanceSheet() {
        List<AccountLineDto> assetLines = new ArrayList<>();
        BigDecimal totalAssets = BigDecimal.ZERO;

        List<ChartOfAccount> assetAccounts = chartOfAccountRepository.findByType(AccountType.ASSET);
        for (ChartOfAccount account : assetAccounts) {
            BigDecimal debits = journalEntryRepository.sumDebitByAccountId(account.getId());
            BigDecimal credits = journalEntryRepository.sumCreditByAccountId(account.getId());
            BigDecimal netBalance = debits.subtract(credits);

            assetLines.add(AccountLineDto.builder()
                    .accountCode(account.getCode())
                    .accountName(account.getName())
                    .accountType(AccountType.ASSET)
                    .balance(netBalance)
                    .build());
            totalAssets = totalAssets.add(netBalance);
        }

        List<AccountLineDto> liabilityLines = new ArrayList<>();
        BigDecimal totalLiabilities = BigDecimal.ZERO;

        List<ChartOfAccount> liabilityAccounts = chartOfAccountRepository.findByType(AccountType.LIABILITY);
        for (ChartOfAccount account : liabilityAccounts) {
            BigDecimal debits = journalEntryRepository.sumDebitByAccountId(account.getId());
            BigDecimal credits = journalEntryRepository.sumCreditByAccountId(account.getId());
            BigDecimal netBalance = credits.subtract(debits); // Liabilities credit-positive

            liabilityLines.add(AccountLineDto.builder()
                    .accountCode(account.getCode())
                    .accountName(account.getName())
                    .accountType(AccountType.LIABILITY)
                    .balance(netBalance)
                    .build());
            totalLiabilities = totalLiabilities.add(netBalance);
        }

        // Retained earnings = cumulative Net Income (Total Income - Total Expenses)
        ProfitAndLossDto pnl = generateProfitAndLoss();
        BigDecimal retainedEarnings = pnl.getNetProfitOrLoss();
        BigDecimal totalLiabilitiesAndEquity = totalLiabilities.add(retainedEarnings);
        boolean balanced = totalAssets.compareTo(totalLiabilitiesAndEquity) == 0;

        return BalanceSheetDto.builder()
                .generatedAt(LocalDateTime.now())
                .assets(assetLines)
                .totalAssets(totalAssets)
                .liabilities(liabilityLines)
                .totalLiabilities(totalLiabilities)
                .retainedEarnings(retainedEarnings)
                .totalLiabilitiesAndEquity(totalLiabilitiesAndEquity)
                .balanced(balanced)
                .build();
    }

    /**
     * 2. Profit & Loss — income (service fee) minus expenses (electricity + repair).
     * Generated directly from double-entry journal entries.
     */
    public ProfitAndLossDto generateProfitAndLoss() {
        List<AccountLineDto> incomeLines = new ArrayList<>();
        BigDecimal totalIncome = BigDecimal.ZERO;

        List<ChartOfAccount> incomeAccounts = chartOfAccountRepository.findByType(AccountType.INCOME);
        for (ChartOfAccount account : incomeAccounts) {
            BigDecimal debits = journalEntryRepository.sumDebitByAccountId(account.getId());
            BigDecimal credits = journalEntryRepository.sumCreditByAccountId(account.getId());
            BigDecimal netBalance = credits.subtract(debits); // Income credit-positive

            incomeLines.add(AccountLineDto.builder()
                    .accountCode(account.getCode())
                    .accountName(account.getName())
                    .accountType(AccountType.INCOME)
                    .balance(netBalance)
                    .build());
            totalIncome = totalIncome.add(netBalance);
        }

        List<AccountLineDto> expenseLines = new ArrayList<>();
        BigDecimal totalExpenses = BigDecimal.ZERO;

        List<ChartOfAccount> expenseAccounts = chartOfAccountRepository.findByType(AccountType.EXPENSE);
        for (ChartOfAccount account : expenseAccounts) {
            BigDecimal debits = journalEntryRepository.sumDebitByAccountId(account.getId());
            BigDecimal credits = journalEntryRepository.sumCreditByAccountId(account.getId());
            BigDecimal netBalance = debits.subtract(credits); // Expenses debit-positive

            expenseLines.add(AccountLineDto.builder()
                    .accountCode(account.getCode())
                    .accountName(account.getName())
                    .accountType(AccountType.EXPENSE)
                    .balance(netBalance)
                    .build());
            totalExpenses = totalExpenses.add(netBalance);
        }

        BigDecimal netProfitOrLoss = totalIncome.subtract(totalExpenses);

        return ProfitAndLossDto.builder()
                .generatedAt(LocalDateTime.now())
                .income(incomeLines)
                .totalIncome(totalIncome)
                .expenses(expenseLines)
                .totalExpenses(totalExpenses)
                .netProfitOrLoss(netProfitOrLoss)
                .build();
    }

    /**
     * 3. Budget Report — zone-wise planned vs actual variance.
     */
    public BudgetReportDto generateBudgetReport(Integer fiscalYear) {
        int year = fiscalYear != null ? fiscalYear : 2026;
        List<BudgetService.BudgetComparisonDto> zoneReports = budgetService.getAllBudgetComparisons(year);

        BigDecimal totalPlanned = BigDecimal.ZERO;
        BigDecimal totalActual = BigDecimal.ZERO;

        for (BudgetService.BudgetComparisonDto line : zoneReports) {
            totalPlanned = totalPlanned.add(line.getPlannedAmount());
            totalActual = totalActual.add(line.getActualAmount());
        }

        BigDecimal totalVariance = totalPlanned.subtract(totalActual);

        return BudgetReportDto.builder()
                .generatedAt(LocalDateTime.now())
                .fiscalYear(year)
                .zoneReports(zoneReports)
                .totalPlanned(totalPlanned)
                .totalActual(totalActual)
                .totalVariance(totalVariance)
                .build();
    }
}
