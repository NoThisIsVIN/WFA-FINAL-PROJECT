package com.metrocitypower.portal.mocks;

/** mock-services call failed. Rendered as 502 MOCKS_ERROR. */
public class MocksException extends RuntimeException {
    public MocksException(String message, Throwable cause) {
        super(message, cause);
    }
}
