package com.wangqi.onboarding.controller;

import com.wangqi.onboarding.dto.OnboardingTaskResponse;
import com.wangqi.onboarding.repository.OnboardingTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only view of the onboarding work created by the consumer.
 *
 * <p>The endpoint is served by the onboarding service and reached through the
 * gateway at {@code /api/onboarding/tasks}; it authenticates the same JWT the
 * employee API issued, without a shared user table.
 *
 * @author wangqi
 */
@RestController
@RequestMapping("/api/onboarding/tasks")
@RequiredArgsConstructor
public class OnboardingTaskController {

    private final OnboardingTaskRepository onboardingTaskRepository;

    @GetMapping
    public List<OnboardingTaskResponse> listTasks() {
        return onboardingTaskRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(OnboardingTaskResponse::from)
                .toList();
    }
}
