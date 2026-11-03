package com.wangqi.onboarding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the onboarding service.
 *
 * <p>This process owns the onboarding workflow and the {@code employee.created}
 * consumer. It has no compile-time knowledge of the employee API — only the
 * shared {@code contracts} artifact — so it can be deployed independently.
 *
 * @author wangqi
 */
@SpringBootApplication
public class OnboardingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OnboardingServiceApplication.class, args);
    }
}
