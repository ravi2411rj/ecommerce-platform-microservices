package com.ecommerce.paymentservice.controller;

import com.ecommerce.paymentservice.dto.PaymentRequestDto;
import com.ecommerce.paymentservice.dto.PaymentResponseDto;
import com.ecommerce.paymentservice.exception.ErrorResponse;
import com.ecommerce.paymentservice.exception.OrderNotFoundException;
import com.ecommerce.paymentservice.model.PaymentStatus;
import com.ecommerce.paymentservice.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.util.List;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payment Processing", description = "APIs for handling payment transactions and statuses.")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Process a new payment", description = "Initiates and processes a payment for a given order.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Payment processed successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid payment details or order status"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "503", description = "Order Service is unavailable or communication error"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponseDto processPayment(@Valid @RequestBody PaymentRequestDto paymentRequestDto) {
        return paymentService.processPayment(paymentRequestDto);
    }

    @Operation(summary = "Get payment by ID", description = "Retrieves payment details by its unique ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public PaymentResponseDto getPaymentById(@PathVariable Long id) {
        return paymentService.getPaymentById(id);
    }

    @Operation(summary = "Get payment by Order ID", description = "Retrieves payment details for a specific order ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Payment not found for the given order ID")
    })
    @GetMapping("/order/{orderId}")
    @ResponseStatus(HttpStatus.OK)
    public PaymentResponseDto getPaymentByOrderId(@PathVariable Long orderId) {
        return paymentService.getPaymentByOrderId(orderId);
    }

    @Operation(summary = "Get all payments", description = "Retrieves a list of all payments.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of payments retrieved",
                    content = @Content(mediaType = "application/json", schema = @Schema(type = "array", implementation = PaymentResponseDto.class)))
    })
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<PaymentResponseDto> getAllPayments() {
        return paymentService.getAllPayments();
    }

    @Operation(summary = "Update payment status", description = "Updates the status of an existing payment.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment status updated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Payment not found"),
            @ApiResponse(responseCode = "400", description = "Invalid status provided")
    })
    @PutMapping("/{id}/status")
    @ResponseStatus(HttpStatus.OK)
    public PaymentResponseDto updatePaymentStatus(@PathVariable Long id, @RequestParam PaymentStatus status) {
        return paymentService.updatePaymentStatus(id, status);
    }

    @Operation(summary = "Delete a payment", description = "Deletes a payment record by ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Payment deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePayment(@PathVariable Long id) {
        paymentService.deletePayment(id);
    }

    @ExceptionHandler(OrderNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleOrderNotFoundException(OrderNotFoundException ex, WebRequest request) {
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                request.getDescription(false)
        );
    }
}