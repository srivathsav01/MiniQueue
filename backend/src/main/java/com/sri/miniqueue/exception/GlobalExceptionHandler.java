package com.sri.miniqueue.exception;

import com.sri.miniqueue.dto.Response;
import com.sri.miniqueue.mapper.ResponseBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps every failure to an HTTP status that tells the client what to do next:
 *   2xx  success (204 = nothing to consume right now, poll again later)
 *   4xx  the request itself is wrong; retrying the same request will not help
 *   5xx  the server failed; the same request may succeed if retried
 *
 * Spring picks the most specific handler, so the subclasses of CustomException are
 * matched before the CustomException handler, and that before the Exception handler.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoMessageAvailableException.class)
    public ResponseEntity<Void> handleNoMessageAvailable(NoMessageAvailableException ex) {
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Response<String>> handleNotFound(ResourceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Response<String>> handleConflict(ConflictException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Response<String>> handleUnauthorized(UnauthorizedException ex) {
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<Response<String>> handleCustomException(CustomException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Malformed JSON body, e.g. a missing quote or a wrong type.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Response<String>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "Malformed JSON request body");
    }

    // A path or query parameter of the wrong type, e.g. a message id that is not a UUID.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Response<String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'");
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    public ResponseEntity<Response<String>> handlePayloadTooLarge(PayloadTooLargeException ex) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, ex.getMessage());
    }

    // Safety net: a constraint violation the service didn't translate itself is still the
    // client's conflict, not a server bug. Log it so we notice and add a proper message.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Response<String>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Untranslated constraint violation", ex);
        return error(HttpStatus.CONFLICT, "The request conflicts with existing data");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<String>> handleGenericException(Exception ex) {
        // Spring's own web exceptions (missing query parameter, unknown URL, wrong HTTP
        // method...) already know their correct status. Keep it instead of turning them into 500.
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            return ResponseEntity.status(status)
                    .body(ResponseBuilder.createErrorResponse(status.value(), errorResponse.getBody().getDetail()));
        }
        // A real bug. Log the details for us; never send internal error text to the client,
        // because it can leak SQL, class names or stack details.
        log.error("Unhandled exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<Response<String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ResponseBuilder.createErrorResponse(status.value(), message));
    }
}
