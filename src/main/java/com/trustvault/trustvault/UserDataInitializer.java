
package com.trustvault.trustvault;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class UserDataInitializer implements CommandLineRunner {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_PASSWORD}")
    private String adminPassword;

    @Value("${OWNER_PASSWORD}")
    private String ownerPassword;

    @Value("${VIEWER_PASSWORD}")
    private String viewerPassword;

    public UserDataInitializer(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {

        createUserIfMissing("admin", adminPassword, "ADMIN");
        createUserIfMissing("owner", ownerPassword, "OWNER");
        createUserIfMissing("viewer", viewerPassword, "VIEWER");
    }

    private void createUserIfMissing(
            String username,
            String password,
            String role) {

        if (appUserRepository.existsByUsername(username)) {
            System.out.println(
                    "TrustVault account already exists: " + username);
            return;
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);

        appUserRepository.save(user);

        System.out.println(
                "TrustVault account created: " + username);
    }
}
