package com.framework.exceptions;

/**
 * Thrown when a Web (or Mobile, from Phase 6) element interaction fails: an
 * explicit wait times out, or the underlying driver throws while
 * clicking/typing/reading an element. Wraps the original Selenium exception
 * as the cause so the real stack trace is never lost.
 */
public class ElementInteractionException extends FrameworkException {

    public ElementInteractionException(String message) {
        super(message);
    }

    public ElementInteractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
