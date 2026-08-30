package com.velocura.ai;

public class GeminiServiceException extends RuntimeException {
    public GeminiServiceException(String msg) { super(msg); }
    public GeminiServiceException(String msg, Throwable cause) { super(msg, cause); }
}
