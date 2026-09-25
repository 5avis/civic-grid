package com.example.civicgrid.repository;

import com.example.civicgrid.entity.BillStatus;
import com.example.civicgrid.entity.VendorBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface VendorBillRepository extends JpaRepository<VendorBill, Long> {
    Optional<VendorBill> findByBillNumber(String billNumber);
    List<VendorBill> findByStatus(BillStatus status);
    List<VendorBill> findByZoneId(Long zoneId);

    @Query("SELECT COALESCE(SUM(b.amount), 0) FROM VendorBill b WHERE b.zone.id = :zoneId")
    BigDecimal sumAmountByZoneId(@Param("zoneId") Long zoneId);
}
