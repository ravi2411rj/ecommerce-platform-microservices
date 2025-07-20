package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.dto.OrderItemDto;
import com.ecommerce.orderservice.dto.OrderRequestDto;
import com.ecommerce.orderservice.dto.OrderResponseDto;
import com.ecommerce.orderservice.exception.InsufficientStockException;
import com.ecommerce.orderservice.exception.OrderNotFoundException;
import com.ecommerce.orderservice.exception.ProductNotFoundException;
import com.ecommerce.orderservice.exception.UserNotFoundException;
import com.ecommerce.orderservice.model.Order;
import com.ecommerce.orderservice.model.OrderItem;
import com.ecommerce.orderservice.model.OrderStatus;
import com.ecommerce.orderservice.repository.OrderRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;

    @Value("${application.services.user-service.url}")
    private String userServiceUrl;

    @Value("${application.services.product-service.url}")
    private String productServiceUrl;

    @Transactional
    public OrderResponseDto createOrder(OrderRequestDto orderRequestDto) {
        log.info("Attempting to create order for userId: {}", orderRequestDto.getUserId());

        validateUserExists(orderRequestDto.getUserId());

        List<OrderItem> orderItems = orderRequestDto.getOrderItems().stream()
                .map(itemDto -> {
                    ProductDetails productDetails = getProductDetails(itemDto.getProductId());

                    if (productDetails.getStockQuantity() < itemDto.getQuantity()) {
                        throw new InsufficientStockException("Insufficient stock for product: " + productDetails.getName() + ". Available: " + productDetails.getStockQuantity());
                    }

                    return OrderItem.builder()
                            .productId(itemDto.getProductId())
                            .quantity(itemDto.getQuantity())
                            .unitPrice(productDetails.getPrice())
                            .subtotal(productDetails.getPrice().multiply(BigDecimal.valueOf(itemDto.getQuantity())))
                            .build();
                })
                .toList();

        BigDecimal totalAmount = orderItems.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder()
                .userId(orderRequestDto.getUserId())
                .status(OrderStatus.PENDING)
                .totalAmount(totalAmount)
                .build();

        orderItems.forEach(order::addOrderItem);

        Order savedOrder = orderRepository.save(order);
        log.info("Order created successfully with ID: {} for userId: {}", savedOrder.getId(), savedOrder.getUserId());

        // TODO: Publish "Order Placed" Event to Kafka/RabbitMQ (Future Enhancement)

        return mapToOrderResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderResponseDto getOrderById(Long id) {
        log.info("Fetching order by ID: {}", id);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with ID: " + id));
        log.info("Order with ID '{}' fetched successfully.", id);
        return mapToOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponseDto> getOrdersByUserId(Long userId) {
        log.info("Fetching orders for userId: {}", userId);
        List<Order> orders = orderRepository.findByUserId(userId);
        log.info("Fetched {} orders for userId: {}", orders.size(), userId);
        return orders.stream()
                .map(this::mapToOrderResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public OrderResponseDto updateOrderStatus(Long orderId, OrderStatus newStatus) {
        log.info("Updating status for order ID: {} to {}", orderId, newStatus);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with ID: " + orderId));

        order.setStatus(newStatus);
        Order updatedOrder = orderRepository.save(order);
        log.info("Order ID: {} status updated to {}", updatedOrder.getId(), updatedOrder.getStatus());
        return mapToOrderResponse(updatedOrder);
    }

    @Transactional
    public void deleteOrder(Long id) {
        log.info("Attempting to delete order with ID: {}", id);
        if (!orderRepository.existsById(id)) {
            throw new OrderNotFoundException("Order not found with ID: " + id);
        }
        orderRepository.deleteById(id);
        log.info("Order with ID '{}' deleted successfully.", id);
    }

    // --- Inter-Service Communication Helper Methods ---
    // TODO: Find a better place to put following code to achieve loose coupling (Future Enhancement)

    // DTO for Product Service response (minimal fields needed for order creation)
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ProductDetails {
        private Long id;
        private String name;
        private BigDecimal price;
        private Integer stockQuantity;
    }

    // DTO for User Service response (minimal fields needed for validation)
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class UserDetails {
        private Long id;
        private String username;
    }

    private void validateUserExists(Long userId) {
        log.info("Validating user with ID: {} using User Service at {}", userId, userServiceUrl);
        try {
            webClientBuilder.build().get()
                    .uri(userServiceUrl + "/api/users/{id}", userId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                            clientResponse.bodyToMono(String.class).flatMap(errorBody -> {
                                log.error("User Service returned client error for user ID {}: {}", userId, errorBody);
                                if (clientResponse.statusCode() == HttpStatus.NOT_FOUND) {
                                    return Mono.error(new UserNotFoundException("User not found with ID: " + userId));
                                }
                                return Mono.error(new RuntimeException("Error from User Service: " + clientResponse.statusCode()));
                            })
                    )
                    .bodyToMono(UserDetails.class)
                    .block();
            log.info("User with ID: {} validated successfully.", userId);
        } catch (WebClientResponseException e) {
            log.error("WebClient error during user validation for ID {}: {}", userId, e.getMessage());
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new UserNotFoundException("User not found with ID: " + userId);
            }
            throw new RuntimeException("Failed to validate user with ID: " + userId + ". Error: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during user validation for ID {}: {}", userId, e.getMessage());
            throw new RuntimeException("An unexpected error occurred during user validation: " + e.getMessage(), e);
        }
    }

    private ProductDetails getProductDetails(Long productId) {
        log.info("Fetching product details for ID: {} from Product Service at {}", productId, productServiceUrl);
        try {
            return webClientBuilder.build().get()
                    .uri(productServiceUrl + "/api/products/{id}", productId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                            clientResponse.bodyToMono(String.class).flatMap(errorBody -> {
                                log.error("Product Service returned client error for product ID {}: {}", productId, errorBody);
                                if (clientResponse.statusCode() == HttpStatus.NOT_FOUND) {
                                    return Mono.error(new ProductNotFoundException("Product not found with ID: " + productId));
                                }
                                return Mono.error(new RuntimeException("Error from Product Service: " + clientResponse.statusCode()));
                            })
                    )
                    .bodyToMono(ProductDetails.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("WebClient error during product details fetch for ID {}: {}", productId, e.getMessage());
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ProductNotFoundException("Product not found with ID: " + productId);
            }
            throw new RuntimeException("Failed to fetch product details for ID: " + productId + ". Error: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during product details fetch for ID {}: {}", productId, e.getMessage());
            throw new RuntimeException("An unexpected error occurred during product details fetch: " + e.getMessage(), e);
        }
    }

    // Helper method to map Order entity to OrderResponseDto DTO
    private OrderResponseDto mapToOrderResponse(Order order) {
        List<OrderItemDto> itemDtos = order.getOrderItems().stream()
                .map(item -> OrderItemDto.builder()
                        .productId(item.getProductId())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .subtotal(item.getSubtotal())
                        .build())
                .collect(Collectors.toList());

        return OrderResponseDto.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .orderDate(order.getOrderDate())
                .lastUpdated(order.getLastUpdated())
                .orderItems(itemDtos)
                .build();
    }
}