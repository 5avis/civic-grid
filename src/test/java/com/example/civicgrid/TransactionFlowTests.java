package com.example.civicgrid;

import com.example.civicgrid.entity.*;
import com.example.civicgrid.repository.*;
import com.example.civicgrid.service.ProcurementService;
import com.example.civicgrid.service.RevenueService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TransactionFlowTests {

    @Autowired
    private ProcurementService procurementService;

    @Autowired
    private RevenueService revenueService;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Test
    void testVendorProcurementFlowWithDoubleEntry() {
        // Find seeded vendor, product, zone
        Contact utility = contactRepository.findByName("Electric Utility").orElseThrow();
        Product kwh = productRepository.findByName("Electricity kWh").orElseThrow();
        Zone highwayZone = zoneRepository.findByName("Highway Zone 7").orElseThrow();

        // 1. Purchase Order
        PurchaseOrder po = procurementService.createPurchaseOrder(
                utility.getId(), kwh.getId(), highwayZone.getId(), 5000, new BigDecimal("0.18"), LocalDate.now()
        );
        assertNotNull(po.getId());
        assertEquals(new BigDecimal("900.00"), po.getTotalAmount());
        assertEquals(OrderStatus.CONFIRMED, po.getStatus());

        // 2. Vendor Bill: Debit Electricity Expense (4010), Credit Power Utility Payable (2010)
        VendorBill bill = procurementService.createVendorBill(
                po.getId(), utility.getId(), highwayZone.getId(), kwh.getId(),
                new BigDecimal("900.00"), LocalDate.now(), "4010", "2010"
        );
        assertNotNull(bill.getId());
        assertEquals(BillStatus.UNPAID, bill.getStatus());

        // Verify Journal Entries for Vendor Bill
        List<JournalEntry> billEntries = journalEntryRepository.findByEntryNumber("JE-BILL-" + bill.getId());
        assertEquals(2, billEntries.size());
        BigDecimal totalDebit = billEntries.stream().map(JournalEntry::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = billEntries.stream().map(JournalEntry::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("900.00"), totalDebit);
        assertEquals(new BigDecimal("900.00"), totalCredit);

        // 3. Payment: Debit Power Utility Payable (2010), Credit Cash/Bank (1010)
        Payment payment = procurementService.payVendorBill(
                bill.getId(), LocalDate.now(), "BANK", "2010", "1010"
        );
        assertNotNull(payment.getId());
        assertEquals(PaymentType.OUTBOUND, payment.getPaymentType());
        
        VendorBill paidBill = procurementService.getVendorBillById(bill.getId());
        assertEquals(BillStatus.PAID, paidBill.getStatus());

        // Verify Journal Entries for Payment
        List<JournalEntry> payEntries = journalEntryRepository.findByEntryNumber("JE-PAY-" + payment.getId());
        assertEquals(2, payEntries.size());
        BigDecimal payDebit = payEntries.stream().map(JournalEntry::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal payCredit = payEntries.stream().map(JournalEntry::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("900.00"), payDebit);
        assertEquals(new BigDecimal("900.00"), payCredit);
    }

    @Test
    void testCustomerRevenueFlowWithDoubleEntry() {
        // Find seeded municipality and service product
        Contact municipality = contactRepository.findByName("City Municipality").orElseThrow();
        Product repairService = productRepository.findByName("Repair Service").orElseThrow();

        // 1. Sales Order
        SalesOrder so = revenueService.createSalesOrder(
                municipality.getId(), repairService.getId(), 2, new BigDecimal("200.00"), LocalDate.now()
        );
        assertNotNull(so.getId());
        assertEquals(new BigDecimal("400.00"), so.getTotalAmount());

        // 2. Customer Invoice: Debit Municipality Receivable (1030), Credit Service Fee Income (3010)
        CustomerInvoice invoice = revenueService.createCustomerInvoice(
                so.getId(), municipality.getId(), new BigDecimal("400.00"), LocalDate.now(), "1030", "3010"
        );
        assertNotNull(invoice.getId());
        assertEquals(InvoiceStatus.UNPAID, invoice.getStatus());

        // Verify Journal Entries for Invoice
        List<JournalEntry> invEntries = journalEntryRepository.findByEntryNumber("JE-INV-" + invoice.getId());
        assertEquals(2, invEntries.size());
        BigDecimal invDebit = invEntries.stream().map(JournalEntry::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal invCredit = invEntries.stream().map(JournalEntry::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("400.00"), invDebit);
        assertEquals(new BigDecimal("400.00"), invCredit);

        // 3. Receive Payment: Debit Cash/Bank (1010), Credit Municipality Receivable (1030)
        Payment payment = revenueService.receiveCustomerPayment(
                invoice.getId(), municipality.getId(), new BigDecimal("400.00"), LocalDate.now(), "BANK", "1010", "1030"
        );
        assertNotNull(payment.getId());
        assertEquals(PaymentType.INBOUND, payment.getPaymentType());

        // Verify Journal Entries for Payment
        List<JournalEntry> payEntries = journalEntryRepository.findByEntryNumber("JE-PAY-" + payment.getId());
        assertEquals(2, payEntries.size());
        BigDecimal payDebit = payEntries.stream().map(JournalEntry::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal payCredit = payEntries.stream().map(JournalEntry::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("400.00"), payDebit);
        assertEquals(new BigDecimal("400.00"), payCredit);
    }
}
