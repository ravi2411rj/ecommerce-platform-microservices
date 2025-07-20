package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.dto.*;
import com.ecommerce.userservice.service.AuthService;
import com.ecommerce.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin Operations", description = "APIs for administrator actions (e.g., user creation with roles, user management)")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AuthService authService;
    private final UserService userService;

    @Operation(summary = "Create a new user with a specific role (Admin only)",
            description = "Allows an administrator to create a new user and assign a specific role (e.g., ROLE_USER, ROLE_ADMIN).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User created successfully with specified role",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input or user already exists",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden - Only ADMIN role can access",
                    content = @Content)
    })
    @PostMapping("/users")
    public ResponseEntity<AuthResponseDto> createNewUserWithRole(@Valid @RequestBody AdminUserCreationRequestDto request) {
        RegisterRequestDto registerRequest = RegisterRequestDto.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(request.getPassword())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .build();

        AuthResponseDto response = authService.createAdminUser(registerRequest, request.getRoleName());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(summary = "Update a user's role (Admin only)",
            description = "Allows an administrator to update the role of an existing user.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User role updated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input or role not found",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden - Only ADMIN role can access",
                    content = @Content)
    })
    @PutMapping("/users/{id}/role")
    public ResponseEntity<UserResponseDto> updateUserRole(@PathVariable Long id, @Valid @RequestBody UpdateUserRoleRequestDto request) {
        UserResponseDto updatedUser = userService.updateUserRole(id, request.getRoleName());
        return ResponseEntity.ok(updatedUser);
    }

    @Operation(summary = "Delete a user by ID (Admin only)",
            description = "Allows an administrator to delete an existing user by ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "User deleted successfully",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden - Only ADMIN role can access",
                    content = @Content)
    })
    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long id) {
        userService.deleteUserById(id);
    }

    @Operation(summary = "Get all users (Admin only)",
            description = "Retrieves a list of all registered users.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of users retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Only ADMIN role can access",
                    content = @Content)
    })
    @GetMapping("/users")
    public ResponseEntity<List<UserResponseDto>> getAllUsers() {
        List<UserResponseDto> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }
}