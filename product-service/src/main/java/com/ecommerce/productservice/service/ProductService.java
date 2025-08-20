package com.ecommerce.productservice.service;

import com.ecommerce.productservice.dto.ProductRequestDto;
import com.ecommerce.productservice.dto.ProductResponseDto;
import com.ecommerce.productservice.exception.ProductAlreadyExistsException;
import com.ecommerce.productservice.exception.ProductNotFoundException;
import com.ecommerce.productservice.model.Product;
import com.ecommerce.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;

    @Caching(
            put = { @CachePut(value = "products", key = "#result.id") },
            evict = { @CacheEvict(value = "allProducts", allEntries = true) }
    )
    @Transactional
    public ProductResponseDto createProduct(ProductRequestDto productRequestDto) {
        log.info("Attempting to create product: {}", productRequestDto.getName());
        if (productRepository.existsByName(productRequestDto.getName())) {
            log.warn("Product with name '{}' already exists.", productRequestDto.getName());
            throw new ProductAlreadyExistsException("Product with name '" + productRequestDto.getName() + "' already exists.");
        }

        Product product = Product.builder()
                .name(productRequestDto.getName())
                .description(productRequestDto.getDescription())
                .price(productRequestDto.getPrice())
                .stockQuantity(productRequestDto.getStockQuantity())
                .category(productRequestDto.getCategory())
                .imageUrl(productRequestDto.getImageUrl())
                .build();

        Product savedProduct = productRepository.save(product);
        log.info("Product '{}' created successfully with ID: {}", savedProduct.getName(), savedProduct.getId());
        return mapToProductResponse(savedProduct);
    }

    @Cacheable(value = "products", key = "#id")
    @Transactional(readOnly = true)
    public ProductResponseDto getProductById(Long id) {
        log.info("Fetching product by ID: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with ID: " + id));
        log.info("Product with ID '{}' fetched successfully.", id);
        return mapToProductResponse(product);
    }

    @Cacheable(value = "allProducts")
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getAllProducts() {
        log.info("Fetching all products.");
        List<Product> products = productRepository.findAll();
        log.info("Fetched {} products.", products.size());
        return products.stream()
                .map(this::mapToProductResponse)
                .collect(Collectors.toList());
    }

    @CachePut(value = "products", key = "#id")
    @Transactional
    public ProductResponseDto updateProduct(Long id, ProductRequestDto productRequestDto) {
        log.info("Attempting to update product with ID: {}", id);
        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with ID: " + id));

        if (!existingProduct.getName().equals(productRequestDto.getName()) && productRepository.existsByName(productRequestDto.getName())) {
            log.warn("Cannot update product ID {} because name '{}' already exists for another product.", id, productRequestDto.getName());
            throw new ProductAlreadyExistsException("Product with name '" + productRequestDto.getName() + "' already exists.");
        }

        existingProduct.setName(productRequestDto.getName());
        existingProduct.setDescription(productRequestDto.getDescription());
        existingProduct.setPrice(productRequestDto.getPrice());
        existingProduct.setStockQuantity(productRequestDto.getStockQuantity());
        existingProduct.setCategory(productRequestDto.getCategory());
        existingProduct.setImageUrl(productRequestDto.getImageUrl());

        Product updatedProduct = productRepository.save(existingProduct);
        log.info("Product with ID '{}' updated successfully.", id);
        return mapToProductResponse(updatedProduct);
    }

    @Caching(evict = {
            @CacheEvict(value = "products", key = "#id"),
            @CacheEvict(value = "allProducts", allEntries = true)
    })
    @Transactional
    public void deleteProduct(Long id) {
        log.info("Attempting to delete product with ID: {}", id);
        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException("Product not found with ID: " + id);
        }
        productRepository.deleteById(id);
        log.info("Product with ID '{}' deleted successfully.", id);
    }

    // Helper method to map Product entity to ProductResponseDto DTO
    private ProductResponseDto mapToProductResponse(Product product) {
        return ProductResponseDto.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .category(product.getCategory())
                .imageUrl(product.getImageUrl())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}