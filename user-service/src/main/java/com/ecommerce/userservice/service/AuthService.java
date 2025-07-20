package com.ecommerce.userservice.service;

import com.ecommerce.userservice.dto.AuthResponseDto;
import com.ecommerce.userservice.dto.LoginRequestDto;
import com.ecommerce.userservice.dto.RegisterRequestDto;
import com.ecommerce.userservice.entity.Role;
import com.ecommerce.userservice.entity.User;
import com.ecommerce.userservice.exception.ResourceNotFoundException;
import com.ecommerce.userservice.repository.RoleRepository;
import com.ecommerce.userservice.repository.UserRepository;
import com.ecommerce.userservice.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponseDto registerUser(RegisterRequestDto request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username is already taken!");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already in use!");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .isActive(true)
                .build();

        Role userRole = roleRepository.findByName(Role.RoleName.ROLE_USER)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", Role.RoleName.ROLE_USER.name()));
        user.setRoles(new HashSet<>(Collections.singletonList(userRole)));

        userRepository.save(user);

        String jwtToken = jwtService.generateToken(user);
        return AuthResponseDto.builder()
                .accessToken(jwtToken)
                .userId(user.getId())
                .username(user.getUsername())
                .role(userRole.getName().name())
                .build();
    }

    @Transactional
    public AuthResponseDto createAdminUser(RegisterRequestDto request, Role.RoleName roleName) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username is already taken!");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already in use!");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .isActive(true)
                .build();

        Role designatedRole = roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", roleName.name()));
        user.setRoles(new HashSet<>(Collections.singletonList(designatedRole))); // Or multiple roles if needed

        userRepository.save(user);

        String jwtToken = jwtService.generateToken(user);
        return AuthResponseDto.builder()
                .accessToken(jwtToken)
                .userId(user.getId())
                .username(user.getUsername())
                .role(designatedRole.getName().name())
                .build();
    }

    public AuthResponseDto loginUser(LoginRequestDto request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", request.getUsername()));

        String jwtToken = jwtService.generateToken(user);

        String primaryRole = user.getRoles().stream()
                .map(role -> role.getName().name())
                .findFirst()
                .orElse("NONE");

        return AuthResponseDto.builder()
                .accessToken(jwtToken)
                .userId(user.getId())
                .username(user.getUsername())
                .role(primaryRole)
                .build();
    }
}