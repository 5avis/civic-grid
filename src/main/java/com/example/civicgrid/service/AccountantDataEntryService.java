package com.example.civicgrid.service;

import com.example.civicgrid.dto.TransactionDataEntryRequest;
import com.example.civicgrid.dto.TransactionDataEntryResponse;
import com.example.civicgrid.entity.*;
import com.example.civicgrid.repository.ContactRepository;
import com.example.civicgrid.repository.JournalEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AccountantDataEntryService {

    private final ContactRepository contactRepository;
    private final ProcurementService procurementService;
    private final RevenueService revenueService;
    private final JournalEntryRepository journalEntryRepository;

    /**
     * Processes transaction entry from Accountant:
     * (type: Vendor Bill / Customer Invoice / Payment, amount, date, party, account)
     * -> creates document and corresponding balanced journal entries.
     */
    public TransactionDataEntryResponse processEntry(TransactionDataEntryRequest request) {
        Contact party = resolveParty(request);
        LocalDate date = request.getDate() != null ? request.getDate() : LocalDate.now();
        String type = request.getType().trim().toUpperCase();

        String documentNumber;
        String journalEntryNumber;

        switch (type) {
            case "VENDOR_BILL":
            case "BILL":
                String payableAccount = "2010"; // Default Power Utility Payable
                if (party.getName().toLowerCase().contains("repair")) {
                    payableAccount = "2020"; // Repair Contractor Payable
                }
                VendorBill bill = procurementService.createVendorBill(
                        null,
                        party.getId(),
                        request.getZoneId(),
                        request.getProductId(),
                        request.getAmount(),
                        date,
                        request.getAccountCode(),
                        payableAccount
                );
                documentNumber = bill.getBillNumber();
                journalEntryNumber = "JE-BILL-" + bill.getId();
                break;

            case "CUSTOMER_INVOICE":
            case "INVOICE":
                CustomerInvoice invoice = revenueService.createCustomerInvoice(
                        null,
                        party.getId(),
                        request.getAmount(),
                        date,
                        "1030", // Municipality Receivable
                        request.getAccountCode()
                );
                documentNumber = invoice.getInvoiceNumber();
                journalEntryNumber = "JE-INV-" + invoice.getId();
                break;

            case "PAYMENT":
                if (party.getType() == ContactType.VENDOR) {
                    // Outbound vendor payment
                    Payment outPay = procurementService.createVendorPayment(
                            party.getId(),
                            request.getAmount(),
                            date,
                            "BANK",
                            request.getAccountCode(), // e.g. 2010 Payable
                            "1010" // Cash and Bank
                    );
                    documentNumber = outPay.getPaymentNumber();
                    journalEntryNumber = "JE-PAY-" + outPay.getId();
                } else {
                    // Inbound customer payment
                    Payment inPay = revenueService.receiveCustomerPayment(
                            null,
                            party.getId(),
                            request.getAmount(),
                            date,
                            "BANK",
                            "1010", // Cash and Bank
                            request.getAccountCode() // e.g. 3010 or 1030
                    );
                    documentNumber = inPay.getPaymentNumber();
                    journalEntryNumber = "JE-PAY-" + inPay.getId();
                }
                break;

            default:
                throw new IllegalArgumentException("Unknown transaction type: " + type + ". Supported: VENDOR_BILL, CUSTOMER_INVOICE, PAYMENT");
        }

        List<JournalEntry> entries = journalEntryRepository.findByEntryNumber(journalEntryNumber);
        List<TransactionDataEntryResponse.JournalEntryLineSummary> lines = entries.stream()
                .map(e -> TransactionDataEntryResponse.JournalEntryLineSummary.builder()
                        .accountCode(e.getAccount().getCode())
                        .accountName(e.getAccount().getName())
                        .debit(e.getDebit())
                        .credit(e.getCredit())
                        .build())
                .collect(Collectors.toList());

        return TransactionDataEntryResponse.builder()
                .message("Transaction and balanced double-entry journal created successfully.")
                .transactionType(type)
                .documentNumber(documentNumber)
                .amount(request.getAmount())
                .transactionDate(date)
                .partyName(party.getName())
                .journalEntryNumber(journalEntryNumber)
                .journalLines(lines)
                .build();
    }

    private Contact resolveParty(TransactionDataEntryRequest request) {
        if (request.getPartyId() != null) {
            return contactRepository.findById(request.getPartyId())
                    .orElseThrow(() -> new IllegalArgumentException("Party not found with id: " + request.getPartyId()));
        }
        if (request.getPartyName() != null && !request.getPartyName().trim().isEmpty()) {
            return contactRepository.findByName(request.getPartyName().trim())
                    .orElseThrow(() -> new IllegalArgumentException("Party not found with name: " + request.getPartyName()));
        }
        throw new IllegalArgumentException("Either partyId or partyName must be provided.");
    }
}
