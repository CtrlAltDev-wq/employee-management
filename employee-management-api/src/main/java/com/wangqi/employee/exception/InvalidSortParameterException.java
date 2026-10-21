package com.wangqi.employee.exception;

/**
 * Raised when a client asks to sort by an unsupported field or direction.
 *
 * @author wangqi
 */
public class InvalidSortParameterException extends RuntimeException {

    public InvalidSortParameterException(String message) {
        super(message);
    }
}
