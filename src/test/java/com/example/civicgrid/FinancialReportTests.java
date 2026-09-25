package com.example.civicgrid;

import com.example.civicgrid.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FinancialReportTests {

    @Autowired
    private ReportService reportService;

    @Test
    void testBalanceSheetGenerationAndEquation() {
        ReportService.BalanceSheetDto balanceSheet = reportService.generateBalanceSheet();

        assertNotNull(balanceSheet);
        assertNotNull(balanceSheet.getGeneratedAt());
        assertNotNull(balanceSheet.getAssets());
        assertNotNull(balanceSheet.getLiabilities());

        // Fundamental accounting equation: Assets = Liabilities + Equity
        assertTrue(balanceSheet.isBalanced(), "Balance Sheet must satisfy Assets = Liabilities + Equity");
        assertEquals(balanceSheet.getTotalAssets(), balanceSheet.getTotalLiabilitiesAndEquity());
    }

    @Test
    void testProfitAndLossGeneration() {
        ReportService.ProfitAndLossDto pnl = reportService.generateProfitAndLoss();

        assertNotNull(pnl);
        assertNotNull(pnl.getGeneratedAt());
        assertNotNull(pnl.getIncome());
        assertNotNull(pnl.getExpenses());

        // Equation: Net Profit/Loss = Total Income - Total Expenses
        BigDecimal expectedNet = pnl.getTotalIncome().subtract(pnl.getTotalExpenses());
        assertEquals(expectedNet, pnl.getNetProfitOrLoss());
    }

    @Test
    void testBudgetVarianceReportGeneration() {
        ReportService.BudgetReportDto budgetReport = reportService.generateBudgetReport(2026);

        assertNotNull(budgetReport);
        assertEquals(2026, budgetReport.getFiscalYear());
        assertNotNull(budgetReport.getZoneReports());
        assertFalse(budgetReport.getZoneReports().isEmpty());

        // Verify summary matches sum of zone lines
        BigDecimal sumPlanned = budgetReport.getZoneReports().stream()
                .map(r -> r.getPlannedAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumActual = budgetReport.getZoneReports().stream()
                .map(r -> r.getActualAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(sumPlanned, budgetReport.getTotalPlanned());
        assertEquals(sumActual, budgetReport.getTotalActual());
        assertEquals(sumPlanned.subtract(sumActual), budgetReport.getTotalVariance());
    }
}
