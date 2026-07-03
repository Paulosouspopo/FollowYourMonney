package com.portfolio.tracker.service;

import com.portfolio.tracker.entity.RecurringInvestment;
import com.portfolio.tracker.repository.RecurringInvestmentRepository;
import com.portfolio.tracker.transaction.Transaction;
import com.portfolio.tracker.transaction.TransactionRepository;
import com.portfolio.tracker.transaction.TransactionType;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecurringInvestmentService {

    private final RecurringInvestmentRepository recurringInvestmentRepository;
    private final TransactionRepository transactionRepository;

    public List<RecurringInvestment> getAllByUser(UUID userId) {
        return recurringInvestmentRepository.findByAssetId(userId);
    }

    public RecurringInvestment getById(UUID id) {
        return recurringInvestmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("RecurringInvestment not found: " + id));
    }

    @Transactional
    public RecurringInvestment create(RecurringInvestment recurring) {
        if (recurring.getStartDate() == null) {
            recurring.setStartDate(LocalDateTime.now());
        }
        if (recurring.getNextExecution() == null) {
            recurring.setNextExecution(recurring.getStartDate());
        }
        return recurringInvestmentRepository.save(recurring);
    }

    @Transactional
    public RecurringInvestment update(UUID id, RecurringInvestment updated) {
        RecurringInvestment existing = getById(id);
        existing.setAmount(updated.getAmount());
        existing.setFrequency(updated.getFrequency());
        existing.setStartDate(updated.getStartDate());
        existing.setNextExecution(updated.getNextExecution());
        existing.setEndDate(updated.getEndDate());
        existing.setActive(updated.getActive());
        return recurringInvestmentRepository.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        recurringInvestmentRepository.deleteById(id);
    }

    @Transactional
    public void processRecurringInvestments() {
        List<RecurringInvestment> duelist = recurringInvestmentRepository
                .findByActiveAndNextExecutionBefore(true, LocalDateTime.now());

        for (RecurringInvestment recurring : duelist) {
            Transaction transaction = buildTransaction(recurring);
            transactionRepository.save(transaction);
            scheduleNext(recurring);
            recurringInvestmentRepository.save(recurring);
        }
    }

    private Transaction buildTransaction(RecurringInvestment recurring) {
        Transaction transaction = new Transaction();
        transaction.setAsset(recurring.getAsset());
        transaction.setType(TransactionType.BUY);
        transaction.setTotalAmount(recurring.getAmount());
        transaction.setTransactionDate(LocalDateTime.now());
        return transaction;
    }

    private void scheduleNext(RecurringInvestment recurring) {
        switch (recurring.getFrequency()) {
            case DAILY    -> recurring.setNextExecution(recurring.getNextExecution().plusDays(1));
            case WEEKLY   -> recurring.setNextExecution(recurring.getNextExecution().plusWeeks(1));
            case MONTHLY  -> recurring.setNextExecution(recurring.getNextExecution().plusMonths(1));
            case QUARTERLY -> recurring.setNextExecution(recurring.getNextExecution().plusMonths(3));
            case YEARLY   -> recurring.setNextExecution(recurring.getNextExecution().plusYears(1));
        }

        if (recurring.getEndDate() != null
                && recurring.getNextExecution().isAfter(recurring.getEndDate())) {
            recurring.setActive(false);
        }
    }
}
