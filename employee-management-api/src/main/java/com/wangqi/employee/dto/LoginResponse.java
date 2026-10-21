package com.wangqi.employee.dto;

/**
 * Token returned by a successful login.
 *
 * @author wangqi
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        String username
) {
}
