package com.example.civicgrid.service;

import com.example.civicgrid.entity.Budget;
import com.example.civicgrid.entity.Zone;
import com.example.civicgrid.repository.BudgetRepository;
import com.example.civicgrid.repository.VendorBillRepository;
import com.example.civicgrid.repository.ZoneRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final ZoneRepository zoneRepository;
    private final VendorBillRepository vendorBillRepository;

    @Getter
    @Builder
    public static class BudgetComparisonDto {
        private Long zoneId;
        private String zoneName;
        private Integer fiscalYear;
        private BigDecimal plannedAmount;
        private BigDecimal actualAmount;
        private BigDecimal varianceAmount; // Planned - Actual (positive = remaining budget, negative = overrun)
        private double utilizationPercentage;
        private String status; // UNDER_BUDGET, OVER_BUDGET, ON_BUDGET
    }

    /**
     * Admin configures the planned budget for a specific zone and fiscal year.
     */
    public Budget setPlannedBudget(Long zoneId, Integer fiscalYear, BigDecimal plannedAmount) {
        if (plannedAmount == null || plannedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Planned budget amount must be non-negative.");
        }

        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new IllegalArgumentException("Zone not found with id: " + zoneId));

        Budget budget = budgetRepository.findByZoneIdAndFiscalYear(zoneId, fiscalYear)
                .orElse(Budget.builder()
                        .zone(zone)
                        .fiscalYear(fiscalYear)
                        .actualAmount(BigDecimal.ZERO)
                        .build());

        budget.setPlannedAmount(plannedAmount);

        // Sync current actual spend from vendor bills linked to this zone
        BigDecimal liveActual = getActualSpendForZone(zoneId);
        budget.setActualAmount(liveActual);

        Budget saved = budgetRepository.save(budget);
        log.info("Set planned budget for zone '{}' (year: {}) to {}", zone.getName(), fiscalYear, plannedAmount);
        return saved;
    }

    /**
     * Calculates actual spend per zone aggregated from vendor bills/expenses linked to that zone.
     */
    @Transactional(readOnly = true)
    public BigDecimal getActualSpendForZone(Long zoneId) {
        BigDecimal total = vendorBillRepository.sumAmountByZoneId(zoneId);
        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Returns a comparison between planned budget and actual spend for a single zone.
     */
    @Transactional(readOnly = true)
    public BudgetComparisonDto getBudgetComparison(Long zoneId, Integer fiscalYear) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new IllegalArgumentException("Zone not found with id: " + zoneId));

        Budget budget = budgetRepository.findByZoneIdAndFiscalYear(zoneId, fiscalYear).orElse(null);
        BigDecimal planned = (budget != null) ? budget.getPlannedAmount() : BigDecimal.ZERO;
        BigDecimal actual = getActualSpendForZone(zoneId);

        BigDecimal variance = planned.subtract(actual);
        double utilization = 0.0;
        if (planned.compareTo(BigDecimal.ZERO) > 0) {
            utilization = actual.divide(planned, 4, RoundingMode.HALF_UP).doubleValue() * 100.0;
        }

        String status;
        if (actual.compareTo(planned) > 0) {
            status = "OVER_BUDGET";
        } else if (actual.compareTo(planned) == 0) {
            status = "ON_BUDGET";
        } else {
            status = "UNDER_BUDGET";
        }

        return BudgetComparisonDto.builder()
                .zoneId(zone.getId())
                .zoneName(zone.getName())
                .fiscalYear(fiscalYear)
                .plannedAmount(planned)
                .actualAmount(actual)
                .varianceAmount(variance)
                .utilizationPercentage(Math.round(utilization * 100.0) / 100.0)
                .status(status)
                .build();
    }

    /**
     * Returns planned vs actual budget comparisons across all zones for a fiscal year.
     */
    @Transactional(readOnly = true)
    public List<BudgetComparisonDto> getAllBudgetComparisons(Integer fiscalYear) {
        List<Zone> zones = zoneRepository.findAll();
        List<BudgetComparisonDto> comparisons = new ArrayList<>();

        for (Zone zone : zones) {
            comparisons.add(getBudgetComparison(zone.getId(), fiscalYear));
        }

        return comparisons;
    }

    /**
     * Syncs the stored actualAmount in budgets table with current vendor bills for reporting persistence.
     */
    public void syncAllBudgetActuals(Integer fiscalYear) {
        List<Budget> budgets = budgetRepository.findByFiscalYear(fiscalYear);
        for (Budget b : budgets) {
            BigDecimal actual = getActualSpendForZone(b.getZone().getId());
            b.setActualAmount(actual);
            budgetRepository.save(b);
        }
    }
}
