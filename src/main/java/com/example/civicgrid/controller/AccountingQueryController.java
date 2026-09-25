package com.example.civicgrid.controller;

import com.example.civicgrid.entity.ChartOfAccount;
import com.example.civicgrid.entity.Contact;
import com.example.civicgrid.entity.JournalEntry;
import com.example.civicgrid.service.ChartOfAccountService;
import com.example.civicgrid.service.ContactService;
import com.example.civicgrid.service.JournalEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AccountingQueryController {

    private final JournalEntryService journalEntryService;
    private final ContactService contactService;
    private final ChartOfAccountService chartOfAccountService;

    @GetMapping("/journal-entries")
    public ResponseEntity<List<JournalEntry>> getAllJournalEntries() {
        return ResponseEntity.ok(journalEntryService.getAll());
    }

    @GetMapping("/contacts")
    public ResponseEntity<List<Contact>> getAllContacts() {
        return ResponseEntity.ok(contactService.getAll());
    }

    @GetMapping("/chart-of-accounts")
    public ResponseEntity<List<ChartOfAccount>> getAllAccounts() {
        return ResponseEntity.ok(chartOfAccountService.getAll());
    }
}
