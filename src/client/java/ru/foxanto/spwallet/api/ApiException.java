package ru.foxanto.spwallet.api;

/** Thrown when the SPWorlds API answers with an error, or cannot be reached at all. */
public class ApiException extends RuntimeException {
    public ApiException(String message) {
        super(message);
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
