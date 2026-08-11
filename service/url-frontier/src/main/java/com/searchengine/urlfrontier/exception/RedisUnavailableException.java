package com.searchengine.urlfrontier.exception;

/** Indicates that Redis could not safely perform URL deduplication. */
public class RedisUnavailableException extends RuntimeException {
    public RedisUnavailableException(Throwable cause) {
        super("Redis is unavailable", cause);
    }
}
