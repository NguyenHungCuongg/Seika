package com.seika.identity_service.config;

import com.seika.identity_service.entity.Role;
import com.seika.identity_service.entity.User;
import com.seika.identity_service.repository.RoleRepository;
import com.seika.identity_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class RoleInitializer {

    private static final String ADMIN_ROLE = "ADMIN";

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_INITIAL_ENABLED:false}")
    private boolean adminInitialEnabled;

    @Value("${ADMIN_INITIAL_USERNAME:}")
    private String adminInitialUsername;

    @Value("${ADMIN_INITIAL_PASSWORD:}")
    private String adminInitialPassword;

    @Bean
    public CommandLineRunner initRoles() {
        return args -> {
            Set<Role> roles = Set.of(
                    Role.builder().name("STUDENT").description("Default learner role").build(),
                    Role.builder().name("TEACHER").description("Quiz and content creator role").build(),
                    Role.builder().name(ADMIN_ROLE).description("System administration role").build()
            );
            roles.forEach(role -> roleRepository.findById(role.getName())
                    .orElseGet(() -> roleRepository.save(role)));

            initializeInitialAdmin();
        };
    }

    private void initializeInitialAdmin() {
        if (!adminInitialEnabled) {
            return;
        }

        if (adminInitialUsername.isBlank() || adminInitialPassword.isBlank()) {
            log.warn("ADMIN_INITIAL_ENABLED=true but initial admin credentials are missing. Skipping initial admin setup.");
            return;
        }

        if (userRepository.existsByRoles_Name(ADMIN_ROLE)) {
            log.info("An ADMIN user already exists. Skipping initial admin setup.");
            return;
        }

        if (userRepository.existsByUsername(adminInitialUsername)) {
            log.warn("Initial admin username already exists but without ADMIN role. Skipping automatic creation.");
            return;
        }

        Role adminRole = roleRepository.findById(ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException("ADMIN role not found during initial setup"));

        User adminUser = User.builder()
                .username(adminInitialUsername)
                .password(passwordEncoder.encode(adminInitialPassword))
                .roles(Set.of(adminRole))
                .build();

        userRepository.save(adminUser);
        log.warn("Initial admin account '{}' has been created.", adminInitialUsername);
    }
}
