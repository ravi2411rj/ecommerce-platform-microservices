package com.ecommerce.userservice.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponseDto {
    private String accessToken;
    private String tokenType = "Bearer";
    private Long userId;
    private String username;
    private String role;
}