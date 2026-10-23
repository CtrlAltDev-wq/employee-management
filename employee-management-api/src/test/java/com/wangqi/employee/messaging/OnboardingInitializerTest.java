package com.wangqi.employee.messaging;

import com.wangqi.employee.repository.OnboardingTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the consumer is idempotent: brokers deliver at least once, so the same
 * message must not create a second onboarding task.
 *
 * @author wangqi
 */
@SpringBootTest(properties = "app.demo-users.enabled=false")
class OnboardingInitializerTest {

    @Autowired
    private OnboardingInitializer onboardingInitializer;

    @Autowired
    private OnboardingTaskRepository onboardingTaskRepository;

    @BeforeEach
    void cleanDatabase() {
        onboardingTaskRepository.deleteAll();
    }

    @Test
    @DisplayName("a redelivered message does not create a second onboarding task")
    void shouldIgnoreDuplicateDelivery() {
        EmployeeCreatedMessage message = new EmployeeCreatedMessage(
                101L, "Alice Chen", "alice@example.com", Instant.parse("2026-10-23T02:43:06Z"));

        boolean first = onboardingInitializer.initialize(message);
        boolean second = onboardingInitializer.initialize(message);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(onboardingTaskRepository.count()).isEqualTo(1);
        assertThat(onboardingTaskRepository.existsByEmployeeId(101L)).isTrue();
    }

    @Test
    @DisplayName("different employees produce independent onboarding tasks")
    void shouldInitialiseEachEmployeeOnce() {
        onboardingInitializer.initialize(new EmployeeCreatedMessage(
                201L, "Bob Li", "bob@example.com", Instant.now()));
        onboardingInitializer.initialize(new EmployeeCreatedMessage(
                202L, "Carol Wang", "carol@example.com", Instant.now()));

        assertThat(onboardingTaskRepository.count()).isEqualTo(2);
    }
}
