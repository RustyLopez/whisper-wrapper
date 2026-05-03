package com.chaostensor.whisperwrapper.controller;

public class DuplicateRequestException extends Throwable {

    public DuplicateRequestException() {
    }

    public DuplicateRequestException(final String message) {
        super(message);
    }

    public DuplicateRequestException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public DuplicateRequestException(final Throwable cause) {
        super(cause);
    }

    public DuplicateRequestException(final String message, final Throwable cause, final boolean enableSuppression, final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
