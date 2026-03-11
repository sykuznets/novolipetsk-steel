package com.example.demo.service.impl;

import com.example.demo.domain.Section;
import com.example.demo.error.AppError;
import com.example.demo.exception.ConcurrentUpdateException;
import com.example.demo.service.SectionRetryService;
import com.example.demo.service.SectionTransactionalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SectionRetryServiceImpl implements SectionRetryService {

    private final SectionTransactionalService transactionalService;

    @Override
    @Retryable(
            retryFor = OptimisticLockingFailureException.class,
            maxAttempts = 8,
            backoff = @Backoff(
                    delay = 300L,
                    multiplier = 2,
                    maxDelay = 6000L,
                    random = true
            )
    )
    public Section calculateSection(Section section) {
        long id = section.getId();
        log.info("Attempting section calculation. sectionId={}", id);
        return transactionalService.calculateSectionTransactional(id);
    }

    @Recover
    public Section recover(
            OptimisticLockingFailureException exception,
            Section section
    ) {
        long id = section.getId();

        var context = RetrySynchronizationManager.getContext();
        Integer retries = (context != null) ? context.getRetryCount() : null;

        Long dbVersion = transactionalService.loadCurrentVersion(id);

        log.error(
                "Failed to update entity after {} retries due to optimistic locking. " +
                "entity = {}, id = {}, inputVersion = {}, dbVersion = {}",
                (retries != null ? retries : -1),
                Section.class.getSimpleName(),
                id,
                section.getVersion(),
                dbVersion,
                exception
        );

        throw new ConcurrentUpdateException(
                new AppError("Concurrent update detected for entity id=" + id)
        );
    }

}
