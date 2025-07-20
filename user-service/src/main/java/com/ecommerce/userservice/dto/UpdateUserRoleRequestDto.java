package com.ecommerce.userservice.dto;

import com.ecommerce.userservice.entity.Role;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRoleRequestDto {

    @NotNull(message = "Role name cannot be empty")
    private Role.RoleName roleName;
}