package com.rentcar.api.exception;

import com.rentcar.api.exception.InvalidSearchDateException;
import com.rentcar.api.util.AppClock;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final AppClock appClock;

    @ExceptionHandler(CarNotAvailableException.class)
    public ResponseEntity<?> handleCarNotAvailableException(CarNotAvailableException ex) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "timestamp", appClock.nowUtc(),
                        "error", "Car not available error",
                        "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(InvalidBookingDateException.class)
    public ResponseEntity<?> handleInvalidBookingDateException(InvalidBookingDateException ex) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "timestamp", appClock.nowUtc(),
                        "error", "Invalid booking date error",
                        "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<?> handleBookingNotFoundException(BookingNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "timestamp", appClock.nowUtc(),
                        "error", "Not found",
                        "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(AddonNotFoundException.class)
    public ResponseEntity<?> handleAddonNotFoundException(AddonNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "timestamp", appClock.nowUtc(),
                        "error", "Not found",
                        "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(CarNotFoundException.class)
    public ResponseEntity<?> handleCarNotFoundException(CarNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "timestamp", appClock.nowUtc(),
                        "error", "Not found",
                        "message", ex.getMessage(),
                        "carId", ex.getCarId()
                ));
    }

    @ExceptionHandler(RefundFailedException.class)
    public ResponseEntity<?> handleRefundFailedException(RefundFailedException ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "timestamp", appClock.nowUtc(),
                        "error", "Refund failed",
                        "message", ex.getMessage(),
                        "paymentId", ex.getPaymentId()
                ));
    }

    @ExceptionHandler(BookingCannotBeCancelledException.class)
    public ResponseEntity<?> handleBookingCannotBeCancelledException(BookingCannotBeCancelledException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "timestamp", appClock.nowUtc(),
                        "error", "Conflict",
                        "message", ex.getMessage(),
                        "bookingId", ex.getBookingId()
                ));
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<?> handleCustomerNotFoundForBookingException(CustomerNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "timestamp", appClock.nowUtc(),
                        "error", "Conflict",
                        "message", ex.getMessage(),
                        "bookingId", ex.getCustomerId()
                ));
    }

    @ExceptionHandler(InvalidSearchDateException.class)
    public ResponseEntity<?> handleInvalidSearchDateException(InvalidSearchDateException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "timestamp", appClock.nowUtc(),
                        "error", "Invalid search dates",
                        "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<?> handleBadRequestException(Exception ex) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "timestamp", appClock.nowUtc(),
                        "error", "Bad Request",
                        "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "timestamp", appClock.nowUtc(),
                        "error", "Validation error",
                        "message", ex.getBindingResult().getFieldError() != null
                                ? ex.getBindingResult().getFieldError().getDefaultMessage()
                                : "Validation failed"
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGenericException(Exception ex) {

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "timestamp", appClock.nowUtc(),
                        "error", "Internal server error",
                        "message", ex.getMessage() != null ? ex.getMessage() : "Unexpected error occurred"
                ));
    }
}
