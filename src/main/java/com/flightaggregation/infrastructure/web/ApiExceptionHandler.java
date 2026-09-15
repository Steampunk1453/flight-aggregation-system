package com.flightaggregation.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiErrorResponse handleIllegalArgument(IllegalArgumentException exception, HttpServletRequest request) {
        log.warn("Rejected request: {}", exception.getMessage());
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage(), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiErrorResponse handleMissingParameter(
            MissingServletRequestParameterException exception,
            HttpServletRequest request
    ) {
        log.warn("Rejected request: {}", exception.getMessage());
        return error(
                HttpStatus.BAD_REQUEST,
                "MISSING_REQUIRED_PARAMETER",
                "Missing required parameter: " + exception.getParameterName(),
                request
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiErrorResponse handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        log.warn("Rejected request: {}", exception.getMessage());
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_PARAMETER_TYPE",
                "Invalid value for parameter: " + exception.getName(),
                request
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    ApiErrorResponse handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unexpected error while handling request", exception);
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                request
        );
    }

    private static ApiErrorResponse error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request
    ) {
        return new ApiErrorResponse(Instant.now(), status.value(), code, message, request.getRequestURI());
    }

    record ApiErrorResponse(
            Instant timestamp,
            int status,
            String code,
            String message,
            String path
    ) {
    }
}
