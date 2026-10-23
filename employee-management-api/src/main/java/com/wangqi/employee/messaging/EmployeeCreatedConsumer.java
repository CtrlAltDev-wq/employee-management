package com.wangqi.employee.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code employee.created} and starts the onboarding workflow.
 *
 * <p>The consumer does one small, transactional unit of work and returns. Slow
 * downstream work (mail delivery, provisioning) belongs to other services
 * subscribing to the same exchange — that is the point of the broker: the
 * employee API does not know or care who reacts to the event, and a consumer
 * outage cannot slow down {@code POST /api/employees}.
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
