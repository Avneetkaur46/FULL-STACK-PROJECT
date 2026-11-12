
package com.librahub.service;

import com.librahub.entity.AppUser;
import com.librahub.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminAccountInitializer(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.email:}") String adminEmail,
            @Value("${app.admin.password:}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            System.out.println("Admin setup skipped: configure app.admin.email and app.admin.password.");
            return;
        }

        if (adminPassword.length() < 12) {
            throw new IllegalStateException(
                    "Admin password must be at least 12 characters.");
        }

        String email = adminEmail.trim().toLowerCase();

        AppUser admin = userRepository.findByEmailIgnoreCase(email)
                .orElse(null);

        if (admin == null) {
            admin = new AppUser(
                    email,
                    passwordEncoder.encode(adminPassword),
                    "ADMIN",
                    null
            );
            userRepository.save(admin);
            System.out.println("Initial admin account created.");
        } else {
            if (!"ADMIN".equals(admin.getRole())) {
                throw new IllegalStateException(
                        "Configured admin email belongs to a non-admin account.");
            }

            admin.setPassword(passwordEncoder.encode(adminPassword));
            userRepository.save(admin);
            System.out.println("Admin password updated from application configuration.");
        }
    }
}
