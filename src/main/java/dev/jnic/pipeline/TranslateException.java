package dev.jnic.pipeline;

public class TranslateException extends Exception {
    public TranslateException(String message) {
        super(message);
    }

    public TranslateException(String message, Throwable cause) {
        super(message, cause);
    }
}
