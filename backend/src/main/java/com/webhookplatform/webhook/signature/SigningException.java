package com.webhookplatform.webhook.signature;

public class SigningException extends RuntimeException {
    public SigningException(String message, Throwable cause) { super(message, cause); }
    public SigningException(String message) { super(message); }
}
