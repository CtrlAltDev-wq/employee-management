package com.wangqi.employee.config;

import com.wangqi.employee.entity.Role;
import com.wangqi.employee.entity.UserAccount;
import com.wangqi.employee.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds one administrator and one read-only user so the API can be exercised
 * immediately after checkout. Disable with {@code app.demo-users.enabled=false}
 * in any real deployment.
 *
 * @author wangqi
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.demo-users", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DemoUserInitializer implements ApplicationRunner {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        createIfMissing("admin", "admin123", Role.ROLE_ADMIN);
        createIfMissing("viewer", "viewer123", Role.ROLE_USER);
    }

    private void createIfMissing(String username, String rawPassword, Role role) {
        if (userAccountRepository.findByUsername(username).isPresent()) {
            return;
        }

        userAccountRepository.save(UserAccount.builder()
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .role(role)
                .enabled(true)
                .build());
    }
}
