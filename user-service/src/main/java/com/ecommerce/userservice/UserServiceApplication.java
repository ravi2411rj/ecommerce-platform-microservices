package com.ecommerce.userservice;

import com.ecommerce.userservice.entity.Role;
import com.ecommerce.userservice.repository.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }

    @Bean
    public CommandLineRunner initRoles(RoleRepository roleRepository) {
        return args -> {
            if (roleRepository.findByName(Role.RoleName.ROLE_USER).isEmpty()) {
                roleRepository.save(Role.builder().name(Role.RoleName.ROLE_USER).build());
            }
            if (roleRepository.findByName(Role.RoleName.ROLE_ADMIN).isEmpty()) {
                roleRepository.save(Role.builder().name(Role.RoleName.ROLE_ADMIN).build());
            }
        };
    }
}