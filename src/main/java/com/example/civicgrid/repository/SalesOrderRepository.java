package com.example.civicgrid.repository;

import com.example.civicgrid.entity.SalesOrder;
import com.example.civicgrid.entity.SalesOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {
    Optional<SalesOrder> findByOrderNumber(String orderNumber);
    List<SalesOrder> findByStatus(SalesOrderStatus status);
}
