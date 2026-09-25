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

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class RevenueService {

    private final SalesOrderRepository salesOrderRepository;
    private final CustomerInvoiceRepository customerInvoiceRepository;
    private final PaymentRepository paymentRepository;
    private final ContactRepository contactRepository;
    private final ProductRepository productRepository;
    private final ChartOfAccountRepository chartOfAccountRepository;
    private final JournalRepository journalRepository;
    private final JournalEntryService journalEntryService;

    /**
     * 1. Create Sales Order
     */
    public SalesOrder createSalesOrder(Long customerId, Long productId, Integer quantity, BigDecimal unitPrice, LocalDate orderDate) {
        Contact customer = contactRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with id: " + customerId));
        if (customer.getType() != ContactType.CUSTOMER) {
            throw new IllegalArgumentException("Contact is not a customer: " + customer.getName());
        }

        Product product = productId != null ? productRepository.findById(productId).orElse(null) : null;
        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(quantity));
        String orderNumber = "SO-" + System.currentTimeMillis();

        SalesOrder so = SalesOrder.builder()
                .orderNumber(orderNumber)
                .customer(customer)
                .product(product)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .totalAmount(total)
                .orderDate(orderDate != null ? orderDate : LocalDate.now())
                .status(SalesOrderStatus.CONFIRMED)
                .build();

        return salesOrderRepository.save(so);
    }

    /**
     * 2. Issue Customer Invoice and create matching double-entry journal entry:
     *    Debit: Receivable Account (e.g. 1030 Municipality Receivable)
     *    Credit: Income Account (e.g. 3010 Municipal Grid Service Fee)
     */
    public CustomerInvoice createCustomerInvoice(Long salesOrderId, Long customerId, BigDecimal amount, LocalDate invoiceDate,
                                                 String receivableAccountCode, String incomeAccountCode) {
        SalesOrder so = null;
        if (salesOrderId != null) {
            so = salesOrderRepository.findById(salesOrderId)
                    .orElseThrow(() -> new IllegalArgumentException("Sales Order not found with id: " + salesOrderId));
            so.setStatus(SalesOrderStatus.INVOICED);
            salesOrderRepository.save(so);
        }

        Contact customer = (so != null) ? so.getCustomer() : contactRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with id: " + customerId));

        String invoiceNumber = "INV-" + System.currentTimeMillis();
        CustomerInvoice invoice = CustomerInvoice.builder()
                .invoiceNumber(invoiceNumber)
                .salesOrder(so)
                .customer(customer)
                .amount(amount)
                .invoiceDate(invoiceDate != null ? invoiceDate : LocalDate.now())
                .status(InvoiceStatus.UNPAID)
                .build();

        CustomerInvoice savedInvoice = customerInvoiceRepository.save(invoice);

        // Record matching double-entry in Sales Journal (SJ)
        Journal salesJournal = journalRepository.findByCode("SJ")
                .orElseThrow(() -> new IllegalStateException("Sales Journal (SJ) not found."));

        ChartOfAccount receivableAccount = chartOfAccountRepository.findByCode(receivableAccountCode != null ? receivableAccountCode : "1030")
                .orElseThrow(() -> new IllegalArgumentException("Receivable account not found: " + receivableAccountCode));
        ChartOfAccount incomeAccount = chartOfAccountRepository.findByCode(incomeAccountCode != null ? incomeAccountCode : "3010")
                .orElseThrow(() -> new IllegalArgumentException("Income account not found: " + incomeAccountCode));

        String entryNumber = "JE-INV-" + savedInvoice.getId();
        journalEntryService.recordTransaction(
                entryNumber,
                savedInvoice.getInvoiceDate(),
                salesJournal,
                savedInvoice.getInvoiceNumber(),
                List.of(
                        JournalEntryService.EntryLine.builder()
                                .account(receivableAccount)
                                .debit(amount)
                                .credit(BigDecimal.ZERO)
                                .description("Accounts Receivable for " + savedInvoice.getInvoiceNumber())
                                .build(),
                        JournalEntryService.EntryLine.builder()
                                .account(incomeAccount)
                                .debit(BigDecimal.ZERO)
                                .credit(amount)
                                .description("Grid Service Revenue: " + savedInvoice.getInvoiceNumber() + " - " + customer.getName())
                                .build()
                )
        );

        log.info("Created Customer Invoice {} for {} with matching journal entry {}", savedInvoice.getInvoiceNumber(), amount, entryNumber);
        return savedInvoice;
    }

    /**
     * 3. Receive Payment from Municipality:
     *    Debit: Cash/Bank Account (1010 Cash and Bank)
     *    Credit: Receivable Account (1030 Municipality Receivable) or Direct Income (3010)
     */
    public Payment receiveCustomerPayment(Long invoiceId, Long customerId, BigDecimal amount, LocalDate paymentDate,
                                          String paymentMethod, String bankAccountCode, String creditAccountCode) {
        CustomerInvoice invoice = null;
        Contact customer;

        if (invoiceId != null) {
            invoice = customerInvoiceRepository.findById(invoiceId)
                    .orElseThrow(() -> new IllegalArgumentException("Customer Invoice not found with id: " + invoiceId));
            if (invoice.getStatus() == InvoiceStatus.PAID) {
                throw new IllegalStateException("Invoice " + invoice.getInvoiceNumber() + " is already paid.");
            }
            invoice.setStatus(InvoiceStatus.PAID);
            customerInvoiceRepository.save(invoice);
            customer = invoice.getCustomer();
            if (amount == null) {
                amount = invoice.getAmount();
            }
        } else {
            customer = contactRepository.findById(customerId)
                    .orElseThrow(() -> new IllegalArgumentException("Customer not found with id: " + customerId));
        }

        String paymentNumber = "PAY-IN-" + System.currentTimeMillis();
        Payment payment = Payment.builder()
                .paymentNumber(paymentNumber)
                .paymentType(PaymentType.INBOUND)
                .customerInvoice(invoice)
                .contact(customer)
                .amount(amount)
                .paymentDate(paymentDate != null ? paymentDate : LocalDate.now())
                .paymentMethod(paymentMethod != null ? paymentMethod : "BANK")
                .build();

        Payment savedPayment = paymentRepository.save(payment);

        // Record matching double-entry in Bank/Cash Journal (BK)
        Journal bankJournal = journalRepository.findByCode("BK")
                .orElseThrow(() -> new IllegalStateException("Bank/Cash Journal (BK) not found."));

        ChartOfAccount bankAccount = chartOfAccountRepository.findByCode(bankAccountCode != null ? bankAccountCode : "1010")
                .orElseThrow(() -> new IllegalArgumentException("Bank account not found: " + bankAccountCode));

        // If paying against an invoice, credit receivable (1030). If direct fee payment, credit income (3010).
        String defaultCredit = (invoice != null) ? "1030" : "3010";
        String creditCode = creditAccountCode != null ? creditAccountCode : defaultCredit;
        ChartOfAccount creditAccount = chartOfAccountRepository.findByCode(creditCode)
                .orElseThrow(() -> new IllegalArgumentException("Credit account not found: " + creditCode));

        String entryNumber = "JE-PAY-" + savedPayment.getId();
        journalEntryService.recordTransaction(
                entryNumber,
                savedPayment.getPaymentDate(),
                bankJournal,
                savedPayment.getPaymentNumber(),
                List.of(
                        JournalEntryService.EntryLine.builder()
                                .account(bankAccount)
                                .debit(savedPayment.getAmount())
                                .credit(BigDecimal.ZERO)
                                .description("Bank receipt from " + customer.getName())
                                .build(),
                        JournalEntryService.EntryLine.builder()
                                .account(creditAccount)
                                .debit(BigDecimal.ZERO)
                                .credit(savedPayment.getAmount())
                                .description("Settlement/Revenue from " + savedPayment.getPaymentNumber())
                                .build()
                )
        );

        log.info("Recorded receipt payment {} of {} with matching journal entry {}", savedPayment.getPaymentNumber(), amount, entryNumber);
        return savedPayment;
    }

    @Transactional(readOnly = true)
    public CustomerInvoice getCustomerInvoiceById(Long id) {
        return customerInvoiceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer Invoice not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<CustomerInvoice> getAllCustomerInvoices() {
        return customerInvoiceRepository.findAll();
    }
}
