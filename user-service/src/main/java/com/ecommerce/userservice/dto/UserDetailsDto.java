package com.ecommerce.userservice.dto;

import lombok.*;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDetailsDto {
    private Long id;
    private String username;
    private String email;
    private Set<String> roles;
    private Boolean isActive;
}