package com.example.civicgrid.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "journal_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_number", nullable = false, length = 50)
    private String entryNumber;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "journal_id", nullable = false)
    private Journal journal;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "account_id", nullable = false)
    private ChartOfAccount account;

    @Builder.Default
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal debit = BigDecimal.ZERO;

    @Builder.Default
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal credit = BigDecimal.ZERO;

    private String description;

    private String reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;
}
