package com.example.civicgrid.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "budgets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    @Column(name = "planned_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal plannedAmount;

    @Builder.Default
    @Column(name = "actual_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal actualAmount = BigDecimal.ZERO;
}
