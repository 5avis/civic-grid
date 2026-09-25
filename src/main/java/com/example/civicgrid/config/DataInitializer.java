package com.example.civicgrid.config;

import com.example.civicgrid.entity.*;
import com.example.civicgrid.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final ContactRepository contactRepository;
    private final ProductRepository productRepository;
    private final ChartOfAccountRepository chartOfAccountRepository;
    private final ZoneRepository zoneRepository;
    private final JournalRepository journalRepository;
    private final StreetLightRepository streetLightRepository;
    private final BudgetRepository budgetRepository;

    @Override
    public void run(String... args) {
        initContacts();
        initProducts();
        initChartOfAccounts();
        initZones();
        initJournals();
        initStreetLights();
        initBudgets();
        log.info("Master data initialization completed successfully.");
    }

    private void initContacts() {
        if (contactRepository.count() == 0) {
            contactRepository.save(Contact.builder()
                    .name("Electric Utility")
                    .type(ContactType.VENDOR)
                    .email("billing@electricutility.gov")
                    .phone("555-0101")
                    .address("100 Powerhouse Rd")
                    .build());

            contactRepository.save(Contact.builder()
                    .name("Repair Contractor")
                    .type(ContactType.VENDOR)
                    .email("service@lightfixers.com")
                    .phone("555-0102")
                    .address("42 Maintenance Blvd")
                    .build());

            contactRepository.save(Contact.builder()
                    .name("City Municipality")
                    .type(ContactType.CUSTOMER)
                    .email("finance@citygov.org")
                    .phone("555-0100")
                    .address("1 City Hall Square")
                    .build());
            log.info("Seeded default contacts (Electric Utility, Repair Contractor, City Municipality).");
        }
    }

    private void initProducts() {
        if (productRepository.count() == 0) {
            productRepository.save(Product.builder()
                    .name("LED Pole")
                    .type(ProductType.GOOD)
                    .unitPrice(new BigDecimal("750.00"))
                    .build());

            productRepository.save(Product.builder()
                    .name("Electricity kWh")
                    .type(ProductType.GOOD)
                    .unitPrice(new BigDecimal("0.18"))
                    .build());

            productRepository.save(Product.builder()
                    .name("Repair Service")
                    .type(ProductType.SERVICE)
                    .unitPrice(new BigDecimal("200.00"))
                    .build());
            log.info("Seeded default products (LED Pole, Electricity kWh, Repair Service).");
        }
    }

    private void initChartOfAccounts() {
        if (chartOfAccountRepository.count() == 0) {
            // Assets
            chartOfAccountRepository.save(ChartOfAccount.builder()
                    .code("1010")
                    .name("Cash and Bank")
                    .type(AccountType.ASSET)
                    .build());

            chartOfAccountRepository.save(ChartOfAccount.builder()
                    .code("1030")
                    .name("Municipality Receivable")
                    .type(AccountType.ASSET)
                    .build());

            chartOfAccountRepository.save(ChartOfAccount.builder()
                    .code("1500")
                    .name("Street Light Infrastructure")
                    .type(AccountType.ASSET)
                    .build());

            // Liabilities
            chartOfAccountRepository.save(ChartOfAccount.builder()
                    .code("2010")
                    .name("Power Utility Payable")
                    .type(AccountType.LIABILITY)
                    .build());

            chartOfAccountRepository.save(ChartOfAccount.builder()
                    .code("2020")
                    .name("Repair Contractor Payable")
                    .type(AccountType.LIABILITY)
                    .build());

            // Income
            chartOfAccountRepository.save(ChartOfAccount.builder()
                    .code("3010")
                    .name("Municipal Grid Service Fee")
                    .type(AccountType.INCOME)
                    .build());

            // Expenses
            chartOfAccountRepository.save(ChartOfAccount.builder()
                    .code("4010")
                    .name("Electricity Expense")
                    .type(AccountType.EXPENSE)
                    .build());

            chartOfAccountRepository.save(ChartOfAccount.builder()
                    .code("4020")
                    .name("Street Light Repair Expense")
                    .type(AccountType.EXPENSE)
                    .build());

            log.info("Seeded Chart of Accounts (Assets, Liabilities, Income, Expenses).");
        } else {
            if (chartOfAccountRepository.findByCode("1030").isEmpty()) {
                chartOfAccountRepository.save(ChartOfAccount.builder()
                        .code("1030")
                        .name("Municipality Receivable")
                        .type(AccountType.ASSET)
                        .build());
            }
        }
    }

    private void initZones() {
        if (zoneRepository.count() == 0) {
            zoneRepository.save(Zone.builder()
                    .name("Highway Zone 7")
                    .description("Expressway perimeter lighting grid")
                    .build());

            zoneRepository.save(Zone.builder()
                    .name("Downtown Zone 1")
                    .description("City center pedestrian and street corridor")
                    .build());

            zoneRepository.save(Zone.builder()
                    .name("Industrial Zone 3")
                    .description("Warehouse and industrial park grid")
                    .build());
            log.info("Seeded default zones (Highway Zone 7, Downtown Zone 1, Industrial Zone 3).");
        }
    }

    private void initJournals() {
        if (journalRepository.count() == 0) {
            journalRepository.save(Journal.builder()
                    .code("PJ")
                    .name("Purchase Journal")
                    .description("Records vendor bills and supply purchases")
                    .build());

            journalRepository.save(Journal.builder()
                    .code("SJ")
                    .name("Sales Journal")
                    .description("Records customer invoices and service fees")
                    .build());

            journalRepository.save(Journal.builder()
                    .code("BK")
                    .name("Bank/Cash Journal")
                    .description("Records payments, inflows, and bank transactions")
                    .build());
            log.info("Seeded default journals (Purchase Journal, Sales Journal, Bank/Cash Journal).");
        }
    }

    private void initStreetLights() {
        if (streetLightRepository.count() == 0) {
            Zone hwy = zoneRepository.findByName("Highway Zone 7").orElse(null);
            Zone dt = zoneRepository.findByName("Downtown Zone 1").orElse(null);

            if (hwy != null) {
                streetLightRepository.save(StreetLight.builder()
                        .poleCode("POLE-HWY-001")
                        .zone(hwy)
                        .dimmingPercentage(80)
                        .simulatedPowerDraw(120.0)
                        .status("ACTIVE")
                        .build());

                streetLightRepository.save(StreetLight.builder()
                        .poleCode("POLE-HWY-002")
                        .zone(hwy)
                        .dimmingPercentage(60)
                        .simulatedPowerDraw(90.0)
                        .status("ACTIVE")
                        .build());
            }

            if (dt != null) {
                streetLightRepository.save(StreetLight.builder()
                        .poleCode("POLE-DT-001")
                        .zone(dt)
                        .dimmingPercentage(100)
                        .simulatedPowerDraw(150.0)
                        .status("ACTIVE")
                        .build());
            }
            log.info("Seeded initial street lights (POLE-HWY-001, POLE-HWY-002, POLE-DT-001).");
        }
    }

    private void initBudgets() {
        if (budgetRepository.count() == 0) {
            Zone hwy = zoneRepository.findByName("Highway Zone 7").orElse(null);
            Zone dt = zoneRepository.findByName("Downtown Zone 1").orElse(null);
            Zone ind = zoneRepository.findByName("Industrial Zone 3").orElse(null);

            if (hwy != null) {
                budgetRepository.save(Budget.builder()
                        .zone(hwy)
                        .fiscalYear(2026)
                        .plannedAmount(new BigDecimal("50000.00"))
                        .actualAmount(BigDecimal.ZERO)
                        .build());
            }

            if (dt != null) {
                budgetRepository.save(Budget.builder()
                        .zone(dt)
                        .fiscalYear(2026)
                        .plannedAmount(new BigDecimal("35000.00"))
                        .actualAmount(BigDecimal.ZERO)
                        .build());
            }

            if (ind != null) {
                budgetRepository.save(Budget.builder()
                        .zone(ind)
                        .fiscalYear(2026)
                        .plannedAmount(new BigDecimal("20000.00"))
                        .actualAmount(BigDecimal.ZERO)
                        .build());
            }

            log.info("Seeded initial zone budgets for fiscal year 2026.");
        }
    }
}
