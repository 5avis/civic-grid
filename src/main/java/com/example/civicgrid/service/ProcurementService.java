package com.example.civicgrid.service;

import com.example.civicgrid.entity.*;
import com.example.civicgrid.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ProcurementService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final VendorBillRepository vendorBillRepository;
    private final PaymentRepository paymentRepository;
    private final ContactRepository contactRepository;
    private final ProductRepository productRepository;
    private final ZoneRepository zoneRepository;
    private final ChartOfAccountRepository chartOfAccountRepository;
    private final JournalRepository journalRepository;
    private final JournalEntryService journalEntryService;

    /**
     * 1. Create Purchase Order
     */
    public PurchaseOrder createPurchaseOrder(Long vendorId, Long productId, Long zoneId, Integer quantity, BigDecimal unitPrice, LocalDate orderDate) {
        Contact vendor = contactRepository.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found with id: " + vendorId));
        if (vendor.getType() != ContactType.VENDOR) {
            throw new IllegalArgumentException("Contact is not a vendor: " + vendor.getName());
        }

        Product product = productId != null ? productRepository.findById(productId).orElse(null) : null;
        Zone zone = zoneId != null ? zoneRepository.findById(zoneId).orElse(null) : null;

        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(quantity));
        String poNumber = "PO-" + System.currentTimeMillis();

        PurchaseOrder po = PurchaseOrder.builder()
                .orderNumber(poNumber)
                .vendor(vendor)
                .product(product)
                .zone(zone)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .totalAmount(total)
                .orderDate(orderDate != null ? orderDate : LocalDate.now())
                .status(OrderStatus.CONFIRMED)
                .build();

        return purchaseOrderRepository.save(po);
    }

    /**
     * 2. Receive Vendor Bill and create matching double-entry journal entry:
     *    Debit: Expense Account (e.g. 4010 Electricity Expense or 4020 Repair Expense)
     *    Credit: Payable Account (e.g. 2010 Power Utility Payable)
     */
    public VendorBill createVendorBill(Long purchaseOrderId, Long vendorId, Long zoneId, Long productId,
                                       BigDecimal amount, LocalDate billDate,
                                       String expenseAccountCode, String payableAccountCode) {
        PurchaseOrder po = null;
        if (purchaseOrderId != null) {
            po = purchaseOrderRepository.findById(purchaseOrderId)
                    .orElseThrow(() -> new IllegalArgumentException("PO not found with id: " + purchaseOrderId));
            po.setStatus(OrderStatus.BILLED);
            purchaseOrderRepository.save(po);
        }

        Contact vendor = (po != null) ? po.getVendor() : contactRepository.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found with id: " + vendorId));
        Zone zone = (po != null && po.getZone() != null) ? po.getZone() : (zoneId != null ? zoneRepository.findById(zoneId).orElse(null) : null);
        Product product = (po != null && po.getProduct() != null) ? po.getProduct() : (productId != null ? productRepository.findById(productId).orElse(null) : null);

        String billNumber = "BILL-" + System.currentTimeMillis();
        VendorBill bill = VendorBill.builder()
                .billNumber(billNumber)
                .purchaseOrder(po)
                .vendor(vendor)
                .zone(zone)
                .product(product)
                .amount(amount)
                .billDate(billDate != null ? billDate : LocalDate.now())
                .status(BillStatus.UNPAID)
                .build();

        VendorBill savedBill = vendorBillRepository.save(bill);

        // Record matching double-entry in Purchase Journal (PJ)
        Journal purchaseJournal = journalRepository.findByCode("PJ")
                .orElseThrow(() -> new IllegalStateException("Purchase Journal (PJ) not found."));

        ChartOfAccount expenseAccount = chartOfAccountRepository.findByCode(expenseAccountCode)
                .orElseThrow(() -> new IllegalArgumentException("Expense account not found: " + expenseAccountCode));
        ChartOfAccount payableAccount = chartOfAccountRepository.findByCode(payableAccountCode)
                .orElseThrow(() -> new IllegalArgumentException("Payable account not found: " + payableAccountCode));

        String entryNumber = "JE-BILL-" + savedBill.getId();
        journalEntryService.recordTransaction(
                entryNumber,
                savedBill.getBillDate(),
                purchaseJournal,
                savedBill.getBillNumber(),
                List.of(
                        JournalEntryService.EntryLine.builder()
                                .account(expenseAccount)
                                .debit(amount)
                                .credit(BigDecimal.ZERO)
                                .description("Vendor Bill: " + savedBill.getBillNumber() + " - " + vendor.getName())
                                .zone(zone)
                                .build(),
                        JournalEntryService.EntryLine.builder()
                                .account(payableAccount)
                                .debit(BigDecimal.ZERO)
                                .credit(amount)
                                .description("Accounts Payable for " + savedBill.getBillNumber())
                                .zone(zone)
                                .build()
                )
        );

        log.info("Created Vendor Bill {} for {} with matching journal entry {}", savedBill.getBillNumber(), amount, entryNumber);
        return savedBill;
    }

    /**
     * 3. Pay Vendor Bill and create matching double-entry journal entry:
     *    Debit: Payable Account (e.g. 2010 Power Utility Payable)
     *    Credit: Cash/Bank Account (1010 Cash and Bank)
     */
    public Payment payVendorBill(Long billId, LocalDate paymentDate, String paymentMethod,
                                 String payableAccountCode, String bankAccountCode) {
        VendorBill bill = vendorBillRepository.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor Bill not found with id: " + billId));

        if (bill.getStatus() == BillStatus.PAID) {
            throw new IllegalStateException("Bill " + bill.getBillNumber() + " is already paid.");
        }

        bill.setStatus(BillStatus.PAID);
        vendorBillRepository.save(bill);

        String paymentNumber = "PAY-OUT-" + System.currentTimeMillis();
        Payment payment = Payment.builder()
                .paymentNumber(paymentNumber)
                .paymentType(PaymentType.OUTBOUND)
                .vendorBill(bill)
                .contact(bill.getVendor())
                .amount(bill.getAmount())
                .paymentDate(paymentDate != null ? paymentDate : LocalDate.now())
                .paymentMethod(paymentMethod != null ? paymentMethod : "BANK")
                .build();

        Payment savedPayment = paymentRepository.save(payment);

        // Record matching double-entry in Bank/Cash Journal (BK)
        Journal bankJournal = journalRepository.findByCode("BK")
                .orElseThrow(() -> new IllegalStateException("Bank/Cash Journal (BK) not found."));

        ChartOfAccount payableAccount = chartOfAccountRepository.findByCode(payableAccountCode)
                .orElseThrow(() -> new IllegalArgumentException("Payable account not found: " + payableAccountCode));
        ChartOfAccount bankAccount = chartOfAccountRepository.findByCode(bankAccountCode)
                .orElseThrow(() -> new IllegalArgumentException("Bank account not found: " + bankAccountCode));

        String entryNumber = "JE-PAY-" + savedPayment.getId();
        journalEntryService.recordTransaction(
                entryNumber,
                savedPayment.getPaymentDate(),
                bankJournal,
                savedPayment.getPaymentNumber(),
                List.of(
                        JournalEntryService.EntryLine.builder()
                                .account(payableAccount)
                                .debit(savedPayment.getAmount())
                                .credit(BigDecimal.ZERO)
                                .description("Settlement of bill: " + bill.getBillNumber())
                                .zone(bill.getZone())
                                .build(),
                        JournalEntryService.EntryLine.builder()
                                .account(bankAccount)
                                .debit(BigDecimal.ZERO)
                                .credit(savedPayment.getAmount())
                                .description("Bank disbursement for " + savedPayment.getPaymentNumber())
                                .zone(bill.getZone())
                                .build()
                )
        );

        log.info("Recorded payment {} for bill {} with matching journal entry {}", savedPayment.getPaymentNumber(), bill.getBillNumber(), entryNumber);
        return savedPayment;
    }

    /**
     * Direct vendor disbursement:
     * Debit: Payable Account (e.g. 2010 or 2020)
     * Credit: Cash/Bank Account (1010)
     */
    public Payment createVendorPayment(Long vendorId, BigDecimal amount, LocalDate paymentDate, String paymentMethod,
                                       String payableAccountCode, String bankAccountCode) {
        Contact vendor = contactRepository.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found with id: " + vendorId));

        String paymentNumber = "PAY-OUT-" + System.currentTimeMillis();
        Payment payment = Payment.builder()
                .paymentNumber(paymentNumber)
                .paymentType(PaymentType.OUTBOUND)
                .contact(vendor)
                .amount(amount)
                .paymentDate(paymentDate != null ? paymentDate : LocalDate.now())
                .paymentMethod(paymentMethod != null ? paymentMethod : "BANK")
                .build();

        Payment savedPayment = paymentRepository.save(payment);

        Journal bankJournal = journalRepository.findByCode("BK")
                .orElseThrow(() -> new IllegalStateException("Bank/Cash Journal (BK) not found."));

        ChartOfAccount payableAccount = chartOfAccountRepository.findByCode(payableAccountCode)
                .orElseThrow(() -> new IllegalArgumentException("Payable account not found: " + payableAccountCode));
        ChartOfAccount bankAccount = chartOfAccountRepository.findByCode(bankAccountCode)
                .orElseThrow(() -> new IllegalArgumentException("Bank account not found: " + bankAccountCode));

        String entryNumber = "JE-PAY-" + savedPayment.getId();
        journalEntryService.recordTransaction(
                entryNumber,
                savedPayment.getPaymentDate(),
                bankJournal,
                savedPayment.getPaymentNumber(),
                List.of(
                        JournalEntryService.EntryLine.builder()
                                .account(payableAccount)
                                .debit(savedPayment.getAmount())
                                .credit(BigDecimal.ZERO)
                                .description("Vendor disbursement: " + vendor.getName())
                                .build(),
                        JournalEntryService.EntryLine.builder()
                                .account(bankAccount)
                                .debit(BigDecimal.ZERO)
                                .credit(savedPayment.getAmount())
                                .description("Bank disbursement for " + savedPayment.getPaymentNumber())
                                .build()
                )
        );

        log.info("Recorded direct vendor payment {} for {} with matching journal entry {}", savedPayment.getPaymentNumber(), amount, entryNumber);
        return savedPayment;
    }

    @Transactional(readOnly = true)
    public VendorBill getVendorBillById(Long id) {
        return vendorBillRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Vendor Bill not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<VendorBill> getAllVendorBills() {
        return vendorBillRepository.findAll();
    }
}
