package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.dto.PaymentRequestDto;
import com.ecommerce.paymentservice.exception.OrderNotFoundException;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private WebClient.Builder webClientBuilder;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, webClientBuilder);
        ReflectionTestUtils.setField(paymentService, "orderServiceUrl", "http://orderservice");
    }

    @Test
    void processPaymentThrowsOrderNotFoundExceptionWhenOrderServiceReturns404() {
        when(paymentRepository.existsByOrderId(anyLong())).thenReturn(false);

        ExchangeFunction exchangeFunction = request -> Mono.just(
                ClientResponse.create(HttpStatus.NOT_FOUND).body("Order not found").build()
        );
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        when(webClientBuilder.build()).thenReturn(webClient);

        PaymentRequestDto requestDto = PaymentRequestDto.builder()
                .orderId(1L)
                .paymentMethod("CARD")
                .amount(BigDecimal.TEN)
                .build();

        assertThrows(OrderNotFoundException.class, () -> paymentService.processPayment(requestDto));
    }
}
