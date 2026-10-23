package com.wangqi.employee.messaging;

import com.wangqi.employee.entity.OnboardingTask;
import com.wangqi.employee.repository.OnboardingTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Starts onboarding for a newly created employee.
 *
 * <p>Idempotent by design: brokers deliver at least once, so handling the same
 * message twice must produce the same state, not two onboarding tasks. The
 * {@code employee_id} unique constraint backs the check up at the database
 * level.
 *
 * @author wangqi
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingInitializer {

    private final OnboardingTaskRepository onboardingTaskRepository;

    /**
     * @return {@code true} when onboarding was started by this call,
     *         {@code false} when the message was a duplicate delivery.
     */
    @Transactional
    public boolean initialize(EmployeeCreatedMessage message) {
        if (onboardingTaskRepository.existsByEmployeeId(message.employeeId())) {
            log.info("Duplicate delivery for employee {} ignored: onboarding already initialised",
                    message.employeeId());
            return false;
        }

        onboardingTaskRepository.save(OnboardingTask.builder()
                .employeeId(message.employeeId())
                .fullName(message.fullName())
                .email(message.email())
                .build());

        log.info("Onboarding initialised for employee {} <{}>",
                message.employeeId(), message.email());
        return true;
    }
}
