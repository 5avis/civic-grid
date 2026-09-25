package com.example.civicgrid.controller;

import com.example.civicgrid.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/balance-sheet")
    public ResponseEntity<ReportService.BalanceSheetDto> getBalanceSheet() {
        return ResponseEntity.ok(reportService.generateBalanceSheet());
    }

    @GetMapping("/pnl")
    public ResponseEntity<ReportService.ProfitAndLossDto> getProfitAndLoss() {
        return ResponseEntity.ok(reportService.generateProfitAndLoss());
    }

    @GetMapping("/budget")
    public ResponseEntity<ReportService.BudgetReportDto> getBudgetReport(
            @RequestParam(required = false, defaultValue = "2026") Integer year
    ) {
        return ResponseEntity.ok(reportService.generateBudgetReport(year));
    }
}
