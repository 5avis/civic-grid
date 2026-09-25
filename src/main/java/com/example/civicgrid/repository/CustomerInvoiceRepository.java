package com.example.civicgrid.repository;

import com.example.civicgrid.entity.CustomerInvoice;
import com.example.civicgrid.entity.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerInvoiceRepository extends JpaRepository<CustomerInvoice, Long> {
    Optional<CustomerInvoice> findByInvoiceNumber(String invoiceNumber);
    List<CustomerInvoice> findByStatus(InvoiceStatus status);
}
