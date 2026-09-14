package com.flightaggregation.infrastructure.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiErrorResponse handleIllegalArgument(IllegalArgumentException exception) {
        log.warn("Rejected request: {}", exception.getMessage());
        return new ApiErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiErrorResponse handleMissingParameter(MissingServletRequestParameterException exception) {
        log.warn("Rejected request: {}", exception.getMessage());
        return new ApiErrorResponse("Missing required parameter: " + exception.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        log.warn("Rejected request: {}", exception.getMessage());
        return new ApiErrorResponse("Invalid value for parameter: " + exception.getName());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    ApiErrorResponse handleUnexpected(Exception exception) {
        log.error("Unexpected error while handling request", exception);
        return new ApiErrorResponse("An unexpected error occurred");
    }

    record ApiErrorResponse(String message) {
    }
}

