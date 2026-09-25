package com.example.civicgrid;

import com.example.civicgrid.entity.Budget;
import com.example.civicgrid.entity.Contact;
import com.example.civicgrid.entity.Product;
import com.example.civicgrid.entity.Zone;
import com.example.civicgrid.repository.ContactRepository;
import com.example.civicgrid.repository.ProductRepository;
import com.example.civicgrid.repository.ZoneRepository;
import com.example.civicgrid.service.BudgetService;
import com.example.civicgrid.service.ProcurementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BudgetTrackingTests {

    @Autowired
    private BudgetService budgetService;

    @Autowired
    private ProcurementService procurementService;

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void testSetPlannedBudgetAndComparePlannedVsActual() {
        Zone zone = zoneRepository.findByName("Highway Zone 7").orElseThrow();

        // 1. Admin sets planned budget of $60,000 for fiscal year 2026
        Budget budget = budgetService.setPlannedBudget(zone.getId(), 2026, new BigDecimal("60000.00"));
        assertNotNull(budget.getId());
        assertEquals(new BigDecimal("60000.00"), budget.getPlannedAmount());

        // Initial spend check
        BigDecimal initialSpend = budgetService.getActualSpendForZone(zone.getId());

        // 2. Add an expense: Create a Vendor Bill for $2,500 linked to this zone
        Contact utility = contactRepository.findByName("Electric Utility").orElseThrow();
        Product kwh = productRepository.findByName("Electricity kWh").orElseThrow();

        procurementService.createVendorBill(
                null, utility.getId(), zone.getId(), kwh.getId(),
                new BigDecimal("2500.00"), LocalDate.now(), "4010", "2010"
        );

        // 3. Verify actual spend increased by $2,500
        BigDecimal newSpend = budgetService.getActualSpendForZone(zone.getId());
        assertEquals(initialSpend.add(new BigDecimal("2500.00")), newSpend);

        // 4. Compare planned vs actual
        BudgetService.BudgetComparisonDto comparison = budgetService.getBudgetComparison(zone.getId(), 2026);
        assertEquals(new BigDecimal("60000.00"), comparison.getPlannedAmount());
        assertEquals(newSpend, comparison.getActualAmount());
        assertEquals(new BigDecimal("60000.00").subtract(newSpend), comparison.getVarianceAmount());
        assertEquals("UNDER_BUDGET", comparison.getStatus());
        assertTrue(comparison.getUtilizationPercentage() > 0.0);
    }

    @Test
    void testOverBudgetScenario() {
        Zone zone = zoneRepository.findByName("Industrial Zone 3").orElseThrow();

        // Admin sets a small planned budget of $500
        budgetService.setPlannedBudget(zone.getId(), 2026, new BigDecimal("500.00"));

        // Add expense higher than budget: $1,200 bill
        Contact contractor = contactRepository.findByName("Repair Contractor").orElseThrow();
        Product repair = productRepository.findByName("Repair Service").orElseThrow();

        procurementService.createVendorBill(
                null, contractor.getId(), zone.getId(), repair.getId(),
                new BigDecimal("1200.00"), LocalDate.now(), "4020", "2020"
        );

        // Check comparison
        BudgetService.BudgetComparisonDto comparison = budgetService.getBudgetComparison(zone.getId(), 2026);
        assertEquals("OVER_BUDGET", comparison.getStatus());
        assertTrue(comparison.getVarianceAmount().compareTo(BigDecimal.ZERO) < 0, "Variance should be negative when over budget");
        assertTrue(comparison.getUtilizationPercentage() > 100.0);
    }

    @Test
    void testGetAllBudgetComparisons() {
        List<BudgetService.BudgetComparisonDto> allComparisons = budgetService.getAllBudgetComparisons(2026);
        assertNotNull(allComparisons);
        assertFalse(allComparisons.isEmpty());
        assertTrue(allComparisons.stream().anyMatch(c -> "Highway Zone 7".equals(c.getZoneName())));
    }
}
