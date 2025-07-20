package com.ecommerce.paymentservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class OrderServiceCommunicationException extends RuntimeException {
    public OrderServiceCommunicationException(String message) {
        super(message);
    }

    public OrderServiceCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}