package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.dto.OrderDetailsDto;
import com.ecommerce.paymentservice.dto.PaymentRequestDto;
import com.ecommerce.paymentservice.dto.PaymentResponseDto;
import com.ecommerce.paymentservice.exception.OrderNotFoundException;
import com.ecommerce.paymentservice.exception.OrderServiceCommunicationException;
import com.ecommerce.paymentservice.exception.PaymentAlreadyExistsException;
import com.ecommerce.paymentservice.exception.PaymentNotFoundException;
import com.ecommerce.paymentservice.model.Payment;
import com.ecommerce.paymentservice.model.PaymentStatus;
import com.ecommerce.paymentservice.repository.PaymentRepository;
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
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final WebClient.Builder webClientBuilder;

    @Value("${application.services.order-service.url}")
    private String orderServiceUrl;

    @Transactional
    public PaymentResponseDto processPayment(PaymentRequestDto paymentRequestDto) {
        log.info("Attempting to process payment for orderId: {}", paymentRequestDto.getOrderId());

        if (paymentRepository.existsByOrderId(paymentRequestDto.getOrderId())) {
            log.warn("Payment already exists for order ID: {}", paymentRequestDto.getOrderId());
            throw new PaymentAlreadyExistsException("Payment already exists for order ID: " + paymentRequestDto.getOrderId());
        }

        OrderDetailsDto orderDetails = getOrderDetails(paymentRequestDto.getOrderId());

        if (!"PENDING".equalsIgnoreCase(orderDetails.getStatus())) {
            throw new OrderServiceCommunicationException("Order with ID " + orderDetails.getId() + " is not in PENDING status. Current status: " + orderDetails.getStatus());
        }

        if (orderDetails.getTotalAmount().compareTo(paymentRequestDto.getAmount()) != 0) {
            log.warn("Mismatched amount for order ID {}. Expected: {}, Received: {}",
                    paymentRequestDto.getOrderId(), orderDetails.getTotalAmount(), paymentRequestDto.getAmount());
            throw new OrderServiceCommunicationException("Payment amount mismatch for order ID: " + orderDetails.getId());
        }

        // Simulate External Payment Gateway Call
        // TODO: Integrate RazorPay/Stripe payment gateway
        String transactionId = generateMockTransactionId();
        PaymentStatus paymentStatus = simulateExternalPayment(paymentRequestDto.getAmount());

        // Create Payment Record
        Payment payment = Payment.builder()
                .orderId(paymentRequestDto.getOrderId())
                .userId(orderDetails.getUserId())
                .amount(paymentRequestDto.getAmount())
                .paymentMethod(paymentRequestDto.getPaymentMethod())
                .status(paymentStatus)
                .transactionId(transactionId)
                .build();

        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment processed for order ID: {} with status: {}. Transaction ID: {}",
                savedPayment.getOrderId(), savedPayment.getStatus(), savedPayment.getTransactionId());

        if (paymentStatus == PaymentStatus.COMPLETED) {
            updateOrderStatusInOrderService(savedPayment.getOrderId(), "PROCESSING");
        } else {
            updateOrderStatusInOrderService(savedPayment.getOrderId(), "PAYMENT_FAILED");
        }

        return mapToPaymentResponse(savedPayment);
    }

    @Transactional(readOnly = true)
    public PaymentResponseDto getPaymentById(Long id) {
        log.info("Fetching payment by ID: {}", id);
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with ID: " + id));
        log.info("Payment with ID '{}' fetched successfully.", id);
        return mapToPaymentResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponseDto getPaymentByOrderId(Long orderId) {
        log.info("Fetching payment by order ID: {}", orderId);
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found for order ID: " + orderId));
        log.info("Payment for order ID '{}' fetched successfully.", orderId);
        return mapToPaymentResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponseDto> getAllPayments() {
        log.info("Fetching all payments.");
        List<Payment> payments = paymentRepository.findAll();
        log.info("Fetched {} payments.", payments.size());
        return payments.stream()
                .map(this::mapToPaymentResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public PaymentResponseDto updatePaymentStatus(Long id, PaymentStatus newStatus) {
        log.info("Updating status for payment ID: {} to {}", id, newStatus);
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with ID: " + id));

        payment.setStatus(newStatus);
        Payment updatedPayment = paymentRepository.save(payment);
        log.info("Payment ID: {} status updated to {}", updatedPayment.getId(), updatedPayment.getStatus());
        return mapToPaymentResponse(updatedPayment);
    }

    @Transactional
    public void deletePayment(Long id) {
        log.info("Attempting to delete payment with ID: {}", id);
        if (!paymentRepository.existsById(id)) {
            throw new PaymentNotFoundException("Payment not found with ID: " + id);
        }
        paymentRepository.deleteById(id);
        log.info("Payment with ID '{}' deleted successfully.", id);
    }

    // --- Inter-Service Communication Helper Methods ---

    private OrderDetailsDto getOrderDetails(Long orderId) {
        log.info("Fetching order details for ID: {} from Order Service at {}", orderId, orderServiceUrl);
        try {
            return webClientBuilder.build().get()
                    .uri(orderServiceUrl + "/api/orders/{id}", orderId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        log.error("Order Service returned client error for order ID {}: {}. Status: {}", orderId, errorBody, clientResponse.statusCode());
                                        if (clientResponse.statusCode() == HttpStatus.NOT_FOUND) {
                                            return Mono.<Throwable>error(new OrderNotFoundException("Order not found with ID: " + orderId));
                                        }
                                        return Mono.<Throwable>error(new OrderServiceCommunicationException("Error from Order Service: " + clientResponse.statusCode() + " - " + errorBody));
                                    })
                    )
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        log.error("Order Service returned server error for order ID {}: {}. Status: {}", orderId, errorBody, clientResponse.statusCode());
                                        return Mono.<Throwable>error((new OrderServiceCommunicationException("Order Service internal error for order ID: " + orderId + " - " + errorBody)));
                                    })
                    )
                    .bodyToMono(OrderDetailsDto.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("WebClient error during order details fetch for ID {}: {}", orderId, e.getMessage());
            throw new OrderServiceCommunicationException("Failed to fetch order details for ID: " + orderId + ". Error: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Unexpected error during order details fetch for ID {}: {}", orderId, e.getMessage());
            throw new OrderServiceCommunicationException("An unexpected error occurred during order details fetch: " + e.getMessage(), e);
        }
    }

    private void updateOrderStatusInOrderService(Long orderId, String status) {
        log.info("Attempting to update order status for ID: {} to {} in Order Service at {}", orderId, status, orderServiceUrl);
        try {
            webClientBuilder.build().put()
                    .uri(orderServiceUrl + "/api/orders/{id}/status?status={status}", orderId, status)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        log.error("Order Service returned error during status update for order ID {}: {}. Status: {}", orderId, errorBody, clientResponse.statusCode());
                                        return Mono.<Throwable>error(new OrderServiceCommunicationException("Failed to update order status in Order Service for ID: " + orderId + ". Error: " + errorBody));
                                    })
                    )
                    .bodyToMono(Void.class)
                    .block();
            log.info("Order ID: {} status successfully updated to {} in Order Service.", orderId, status);
        } catch (WebClientResponseException e) {
            log.error("WebClient error during order status update for ID {}: {}", orderId, e.getMessage());
            throw new OrderServiceCommunicationException("Failed to update order status in Order Service for ID: " + orderId + ". Error: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Unexpected error during order status update for ID {}: {}", orderId, e.getMessage());
            throw new OrderServiceCommunicationException("An unexpected error occurred during order status update: " + e.getMessage(), e);
        }
    }

    private String generateMockTransactionId() {
        return "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private PaymentStatus simulateExternalPayment(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            log.info("Simulating successful payment for amount: {}", amount);
            return PaymentStatus.COMPLETED;
        } else {
            log.warn("Simulating failed payment for amount: {}", amount);
            return PaymentStatus.FAILED;
        }
    }

    private PaymentResponseDto mapToPaymentResponse(Payment payment) {
        return PaymentResponseDto.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .userId(payment.getUserId())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .status(payment.getStatus())
                .transactionId(payment.getTransactionId())
                .paymentDate(payment.getPaymentDate())
                .lastUpdated(payment.getLastUpdated())
                .build();
    }
}