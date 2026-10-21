package com.wangqi.employee.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Credentials exchanged for a JWT.
 *
 * @author wangqi
 */
public record LoginRequest(

        @NotBlank(message = "Username is required")
        String username,

        @NotBlank(message = "Password is required")
        String password
) {
}
