package com.example.civicgrid.repository;

import com.example.civicgrid.entity.AccountType;
import com.example.civicgrid.entity.ChartOfAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChartOfAccountRepository extends JpaRepository<ChartOfAccount, Long> {
    Optional<ChartOfAccount> findByCode(String code);
    Optional<ChartOfAccount> findByName(String name);
    List<ChartOfAccount> findByType(AccountType type);
}
