package dev.jnic.pipeline;

public class TranslateError extends RuntimeException {
    public TranslateError(String message) {
        super(message);
    }

    public TranslateError(String message, Throwable cause) {
        super(message, cause);
    }
}
