package com.ecommerce.userservice.config;

import com.ecommerce.userservice.repository.UserRepository;
import com.ecommerce.userservice.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor // For injecting final fields via constructor
@EnableMethodSecurity(prePostEnabled = true) // Enables @PreAuthorize
public class SecurityConfiguration {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider; // Your custom authentication provider

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // Disable CSRF for stateless REST APIs
                .authorizeHttpRequests(authorize -> authorize
                        // Allow public access to authentication endpoints
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        // Allow public access to Swagger UI and API Docs
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                        // All other requests require authentication
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // JWTs are stateless
                .authenticationProvider(authenticationProvider) // Set your custom authentication provider
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class); // Add your JWT filter before the standard username/password filter

        return http.build();
    }

    @Component("userSecurity") // Name the bean so it can be referenced by SpEL
    public static class UserSecurity {

        private final UserRepository userRepository; // Inject UserRepository

        // Constructor to inject UserRepository
        public UserSecurity(UserRepository userRepository) {
            this.userRepository = userRepository;
        }

        public boolean isSelf(Long userId) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails)) {
                // It should be UserDetails, not org.springframework.security.core.userdetails.User directly,
                // as your custom UserDetails implementation might be returned.
                return false;
            }
            // The principal from Spring Security is an instance of org.springframework.security.core.userdetails.UserDetails
            // which wraps your entity's username (or whatever your UserDetails implementation returns as username).
            String username = authentication.getName(); // Get username from authenticated principal

            // Fetch your actual User entity to get its ID
            // Assuming findByUsername returns Optional<UserEntity>
            return userRepository.findByUsername(username)
                    .map(user -> user.getId() != null && user.getId().equals(userId)) // Add null check for user.getId()
                    .orElse(false);
        }
    }
}