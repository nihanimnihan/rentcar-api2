package com.rentcar.api.exception;

import com.rentcar.api.util.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.format.DateTimeParseException;
import java.util.Map;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final AppClock appClock;

    // ── 404 Not Found ──────────────────────────────────────────────────────────

    @ExceptionHandler(CarNotFoundException.class)
    public ResponseEntity<?> handleCarNotFoundException(CarNotFoundException ex) {
        return notFound(ex.getMessage());
    }

    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<?> handleBookingNotFoundException(BookingNotFoundException ex) {
        return notFound(ex.getMessage());
    }

    @ExceptionHandler(AddonNotFoundException.class)
    public ResponseEntity<?> handleAddonNotFoundException(AddonNotFoundException ex) {
        return notFound(ex.getMessage());
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<?> handleCustomerNotFoundException(CustomerNotFoundException ex) {
        return notFound(ex.getMessage());
    }

    @ExceptionHandler(ChauffeurCategoryNotFoundException.class)
    public ResponseEntity<?> handleChauffeurCategoryNotFoundException(ChauffeurCategoryNotFoundException ex) {
        return notFound(ex.getMessage());
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<?> handlePaymentNotFoundException(PaymentNotFoundException ex) {
        return notFound(ex.getMessage());
    }

    /**
     * Missing static resources (e.g. /img/cars/foo.png not found on disk).
     * Spring 6.x throws NoResourceFoundException from ResourceHttpRequestHandler,
     * which bubbles up through the DispatcherServlet and would otherwise hit the
     * catch-all handler below and produce a spurious ERROR log entry.
     * Handled explicitly here: return 404, no stack trace in the logs.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleNoResourceFoundException(NoResourceFoundException ex) {
        log.debug("Static resource not found: {}", ex.getResourcePath());
        return notFound("Resource not found: " + ex.getResourcePath());
    }

    // ── 400 Bad Request ────────────────────────────────────────────────────────

    @ExceptionHandler(InvalidBookingDateException.class)
    public ResponseEntity<?> handleInvalidBookingDateException(InvalidBookingDateException ex) {
        return error(HttpStatus.BAD_REQUEST, "Invalid booking dates", ex.getMessage());
    }

    @ExceptionHandler(InvalidTransferRequestException.class)
    public ResponseEntity<?> handleInvalidTransferRequestException(InvalidTransferRequestException ex) {
        return error(HttpStatus.BAD_REQUEST, "Invalid transfer request", ex.getMessage());
    }

    @ExceptionHandler(InvalidSearchDateException.class)
    public ResponseEntity<?> handleInvalidSearchDateException(InvalidSearchDateException ex) {
        return error(HttpStatus.BAD_REQUEST, "Invalid search dates", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldError() != null
                ? ex.getBindingResult().getFieldError().getDefaultMessage()
                : "Validation failed";
        return error(HttpStatus.BAD_REQUEST, "Validation error", message);
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<?> handleDateTimeParseException(DateTimeParseException ex) {
        log.warn("Invalid datetime parameter: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, "Bad Request",
                "Invalid datetime format. Expected: YYYY-MM-DDTHH:mm (e.g. 2026-05-29T10:00)");
    }

    // ── 409 Conflict ──────────────────────────────────────────────────────────

    @ExceptionHandler(CarNotAvailableException.class)
    public ResponseEntity<?> handleCarNotAvailableException(CarNotAvailableException ex) {
        return error(HttpStatus.CONFLICT, "Car not available", ex.getMessage());
    }

    @ExceptionHandler(NoChauffeurCarAvailableException.class)
    public ResponseEntity<?> handleNoChauffeurCarAvailableException(NoChauffeurCarAvailableException ex) {
        return error(HttpStatus.CONFLICT, "No chauffeur car available", ex.getMessage());
    }

    @ExceptionHandler(BookingCannotBeCancelledException.class)
    public ResponseEntity<?> handleBookingCannotBeCancelledException(BookingCannotBeCancelledException ex) {
        return error(HttpStatus.CONFLICT, "Booking cannot be cancelled", ex.getMessage());
    }

    @ExceptionHandler(InvalidBookingStateException.class)
    public ResponseEntity<?> handleInvalidBookingStateException(InvalidBookingStateException ex) {
        return error(HttpStatus.CONFLICT, "Invalid booking state", ex.getMessage());
    }

    // ── 500 Internal Server Error ─────────────────────────────────────────────

    @ExceptionHandler(RefundFailedException.class)
    public ResponseEntity<?> handleRefundFailedException(RefundFailedException ex) {
        // Log with full detail — operations team must investigate.
        log.error("Refund failed for paymentId={} — manual intervention required", ex.getPaymentId(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Refund failed",
                "The refund could not be processed. Our team has been notified.");
    }

    /**
     * Catch-all for any unhandled exception.
     * IMPORTANT: the internal exception message is never forwarded to the client —
     * it may contain Hibernate details, stack information, or sensitive DB data.
     * Log at ERROR so it is visible in production logs for investigation.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGenericException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred. Please try again later.");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<?> notFound(String message) {
        return error(HttpStatus.NOT_FOUND, "Not found", message);
    }

    private ResponseEntity<?> error(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", appClock.nowUtc(),
                "error", error,
                "message", message
        ));
    }
}
