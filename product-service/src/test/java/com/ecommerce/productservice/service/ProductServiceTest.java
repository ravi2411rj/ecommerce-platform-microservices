package com.ecommerce.productservice.service;

import com.ecommerce.productservice.dto.ProductRequestDto;
import com.ecommerce.productservice.dto.ProductResponseDto;
import com.ecommerce.productservice.model.Product;
import com.ecommerce.productservice.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
class ProductServiceTest {

    @Autowired
    private ProductService productService;

    @MockBean
    private ProductRepository productRepository;

    @Test
    void createProductEvictsAllProductsCache() {
        ProductRequestDto request = ProductRequestDto.builder()
                .name("Phone")
                .description("Smartphone")
                .price(BigDecimal.valueOf(999.99))
                .stockQuantity(5)
                .category("Electronics")
                .imageUrl("http://image")
                .build();

        Product saved = Product.builder()
                .id(1L)
                .name("Phone")
                .description("Smartphone")
                .price(BigDecimal.valueOf(999.99))
                .stockQuantity(5)
                .category("Electronics")
                .imageUrl("http://image")
                .build();

        when(productRepository.existsByName("Phone")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenReturn(saved);
        when(productRepository.findAll()).thenReturn(Collections.emptyList(), List.of(saved));

        // Populate cache with empty list
        assertThat(productService.getAllProducts()).isEmpty();

        // Create product
        ProductResponseDto response = productService.createProduct(request);
        assertThat(response.getId()).isEqualTo(1L);

        // After creation, cache should be evicted and repository called again
        assertThat(productService.getAllProducts()).hasSize(1);
        verify(productRepository, times(2)).findAll();
    }
}
