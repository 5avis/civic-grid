package com.example.civicgrid.repository;

import com.example.civicgrid.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {

    Optional<Budget> findByZoneIdAndFiscalYear(Long zoneId, Integer fiscalYear);

    List<Budget> findByZoneId(Long zoneId);

    List<Budget> findByFiscalYear(Integer fiscalYear);
}
