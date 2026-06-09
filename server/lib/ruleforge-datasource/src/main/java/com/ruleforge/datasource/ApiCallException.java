package com.ruleforge.datasource;

/**
 * V5.23 — Thrown by {@link BaseApiDataSource#fetch(Vars)} when a third-party API call fails
 * or the response cannot be parsed.
 *
 * <p>Framework interceptors ({@code CircuitBreaker}, {@code Retry}) use this exception type
 * to decide whether to fail-fast, retry, or count toward circuit-breaker failure rate.
 */
public class ApiCallException extends RuntimeException {

    public ApiCallException(String message) {
        super(message);
    }

    public ApiCallException(String message, Throwable cause) {
        super(message, cause);
    }

    public ApiCallException(String apiName, String message, Throwable cause) {
        super("[" + apiName + "] " + message, cause);
    }
}
