package com.lhcamposs.tc_corporate_rag.exceptions;

public class LexicalSearchException extends RuntimeException {
    public LexicalSearchException(String message) {
        super(message);
    }

    public LexicalSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
