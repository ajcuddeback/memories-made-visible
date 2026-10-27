package com.memoriesmadevisible.common;

/** The request is fine but collides with what's already on the calendar → 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}
