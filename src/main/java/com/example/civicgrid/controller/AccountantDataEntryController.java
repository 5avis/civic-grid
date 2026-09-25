package com.example.civicgrid.controller;

import com.example.civicgrid.dto.TransactionDataEntryRequest;
import com.example.civicgrid.dto.TransactionDataEntryResponse;
import com.example.civicgrid.service.AccountantDataEntryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class AccountantDataEntryController {

    private final AccountantDataEntryService accountantDataEntryService;

    /**
     * Section 10: Simple endpoint for the Accountant to submit transaction details:
     * (type: Vendor Bill / Customer Invoice / Payment, amount, date, party, account)
     * -> creates the corresponding double-entry journal entry.
     * POST /api/transactions
     */
    @PostMapping
    public ResponseEntity<TransactionDataEntryResponse> submitTransaction(
            @Valid @RequestBody TransactionDataEntryRequest request
    ) {
        TransactionDataEntryResponse response = accountantDataEntryService.processEntry(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
