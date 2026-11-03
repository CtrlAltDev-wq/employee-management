package com.wangqi.onboarding.messaging;

import com.wangqi.contracts.messaging.EmployeeCreatedMessage;
import com.wangqi.onboarding.service.OnboardingInitializer;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code employee.created} and starts the onboarding workflow.
 *
 * <p>This class lives in its own service now. The producer still publishes the
 * same event to the same exchange; nothing on the write path knows where the
 * consumer runs. With {@code spring.rabbitmq.listener.simple.observation-enabled}
 * the listener continues the publisher's trace instead of starting a new one.
 *
 * @author wangqi
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EmployeeCreatedConsumer {

    private final OnboardingInitializer onboardingInitializer;

    @RabbitListener(queues = "${app.messaging.queue}")
    public void onEmployeeCreated(EmployeeCreatedMessage message) {
        onboardingInitializer.initialize(message);
    }
}
