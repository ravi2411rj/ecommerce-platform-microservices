package com.ecommerce.productservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customConfig() {
        return new OpenAPI().info(
                        new Info().title("Product Service APIs")
                                .description("APIs for product catalog management, retrieval, and inventory updates.")
                                .version("1.0.0")
                );
//                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
//                .components(new Components().addSecuritySchemes(
//                                "bearerAuth", new SecurityScheme()
//                                        .type(SecurityScheme.Type.HTTP)
//                                        .scheme("bearer")
//                                        .bearerFormat("JWT")
//                                        .in(SecurityScheme.In.HEADER)
//                                        .name("Authorization")
//                        )
//                );
    }
}