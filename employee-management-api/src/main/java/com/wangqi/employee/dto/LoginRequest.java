package com.wangqi.employee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Credentials exchanged for a JWT.
 *
 * @author wangqi
 */
@Schema(name = "LoginRequest", description = "Credentials exchanged for a JWT")
public record LoginRequest(

        @Schema(description = "Account username", example = "admin",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Username is required")
        String username,

        @Schema(description = "Account password", example = "admin123",
                requiredMode = Schema.RequiredMode.REQUIRED, format = "password")
        @NotBlank(message = "Password is required")
        String password
) {
}
