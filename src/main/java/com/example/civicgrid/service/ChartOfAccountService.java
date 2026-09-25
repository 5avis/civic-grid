package com.example.civicgrid.service;

import com.example.civicgrid.entity.AccountType;
import com.example.civicgrid.entity.ChartOfAccount;
import com.example.civicgrid.repository.ChartOfAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ChartOfAccountService {

    private final ChartOfAccountRepository chartOfAccountRepository;

    public ChartOfAccount create(ChartOfAccount account) {
        return chartOfAccountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public ChartOfAccount getById(Long id) {
        return chartOfAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public ChartOfAccount getByCode(String code) {
        return chartOfAccountRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Account not found with code: " + code));
    }

    @Transactional(readOnly = true)
    public List<ChartOfAccount> getAll() {
        return chartOfAccountRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<ChartOfAccount> getByType(AccountType type) {
        return chartOfAccountRepository.findByType(type);
    }

    public ChartOfAccount update(Long id, ChartOfAccount updated) {
        ChartOfAccount existing = getById(id);
        existing.setCode(updated.getCode());
        existing.setName(updated.getName());
        existing.setType(updated.getType());
        return chartOfAccountRepository.save(existing);
    }

    public void delete(Long id) {
        ChartOfAccount existing = getById(id);
        chartOfAccountRepository.delete(existing);
    }
}
