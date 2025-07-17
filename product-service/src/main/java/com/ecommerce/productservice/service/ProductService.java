package com.ecommerce.productservice.service;

import com.ecommerce.productservice.dto.ProductRequest;
import com.ecommerce.productservice.dto.ProductResponse;
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

    @Transactional
    public ProductResponse createProduct(ProductRequest productRequest) {
        log.info("Attempting to create product: {}", productRequest.getName());
        if (productRepository.existsByName(productRequest.getName())) {
            log.warn("Product with name '{}' already exists.", productRequest.getName());
            throw new ProductAlreadyExistsException("Product with name '" + productRequest.getName() + "' already exists.");
        }

        Product product = Product.builder()
                .name(productRequest.getName())
                .description(productRequest.getDescription())
                .price(productRequest.getPrice())
                .stockQuantity(productRequest.getStockQuantity())
                .category(productRequest.getCategory())
                .imageUrl(productRequest.getImageUrl())
                .build();

        Product savedProduct = productRepository.save(product);
        log.info("Product '{}' created successfully with ID: {}", savedProduct.getName(), savedProduct.getId());
        return mapToProductResponse(savedProduct);
    }

    @Cacheable(value = "products", key = "#id") // Cache individual product by ID
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        log.info("Fetching product by ID: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with ID: " + id));
        log.info("Product with ID '{}' fetched successfully.", id);
        return mapToProductResponse(product);
    }

    @Cacheable(value = "allProducts") // Cache all products
    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {
        log.info("Fetching all products.");
        List<Product> products = productRepository.findAll();
        log.info("Fetched {} products.", products.size());
        return products.stream()
                .map(this::mapToProductResponse)
                .collect(Collectors.toList());
    }

    @CachePut(value = "products", key = "#id") // Update cache entry after update
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest productRequest) {
        log.info("Attempting to update product with ID: {}", id);
        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with ID: " + id));

        // Check for name uniqueness if name is changed
        if (!existingProduct.getName().equals(productRequest.getName()) && productRepository.existsByName(productRequest.getName())) {
            log.warn("Cannot update product ID {} because name '{}' already exists for another product.", id, productRequest.getName());
            throw new ProductAlreadyExistsException("Product with name '" + productRequest.getName() + "' already exists.");
        }

        existingProduct.setName(productRequest.getName());
        existingProduct.setDescription(productRequest.getDescription());
        existingProduct.setPrice(productRequest.getPrice());
        existingProduct.setStockQuantity(productRequest.getStockQuantity());
        existingProduct.setCategory(productRequest.getCategory());
        existingProduct.setImageUrl(productRequest.getImageUrl());

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

    // Helper method to map Product entity to ProductResponse DTO
    private ProductResponse mapToProductResponse(Product product) {
        return ProductResponse.builder()
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