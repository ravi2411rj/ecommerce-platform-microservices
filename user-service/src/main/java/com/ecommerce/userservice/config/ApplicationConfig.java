package com.ecommerce.userservice.config;

import com.ecommerce.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
public class ApplicationConfig {

    private final UserRepository userRepository;

    @Bean
    public UserDetailsService userDetailsService() {
        // This bean is still needed for the AuthenticationProvider
        return username -> userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // This bean is still needed for the AuthenticationProvider
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        // **RESOLVED DEPRECATION WARNINGS HERE:**
        // We now pass UserDetailsService and PasswordEncoder directly to the constructor
        // or use the builder pattern to avoid deprecated setters/constructors.
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(passwordEncoder());
        provider.setUserDetailsService(userDetailsService()); // Still requires this setter for now, even if constructor takes passwordEncoder
        return provider;
    }


    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        // This remains unchanged as it's the standard way to get AuthenticationManager
        return config.getAuthenticationManager();
    }

    @Bean("userSecurity") // Name the bean so it can be referenced by SpEL
    public SecurityConfiguration.UserSecurity userSecurity() {
        return new SecurityConfiguration.UserSecurity(userRepository); // Pass userRepository for checking user ID matches
    }
}