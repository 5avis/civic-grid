package com.example.civicgrid.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_number", nullable = false, unique = true, length = 50)
    private String paymentNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 20)
    private PaymentType paymentType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vendor_bill_id")
    private VendorBill vendorBill;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_invoice_id")
    private CustomerInvoice customerInvoice;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "contact_id", nullable = false)
    private Contact contact;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Builder.Default
    @Column(name = "payment_method", nullable = false, length = 50)
    private String paymentMethod = "BANK";
}
