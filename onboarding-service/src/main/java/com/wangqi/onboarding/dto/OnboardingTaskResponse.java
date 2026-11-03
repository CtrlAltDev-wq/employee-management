package com.wangqi.onboarding.dto;

import com.wangqi.onboarding.entity.OnboardingTask;

import java.time.Instant;

/**
 * Read model for the onboarding queue.
 *
 * @author wangqi
 */
public record OnboardingTaskResponse(
        Long id,
        Long employeeId,
        String fullName,
        String email,
        Instant createdAt
) {

    public static OnboardingTaskResponse from(OnboardingTask task) {
        return new OnboardingTaskResponse(
                task.getId(),
                task.getEmployeeId(),
                task.getFullName(),
                task.getEmail(),
                task.getCreatedAt());
    }
}
