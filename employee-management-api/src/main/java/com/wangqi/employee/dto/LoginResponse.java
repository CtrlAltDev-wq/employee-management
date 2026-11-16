package com.wangqi.employee.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Token returned by a successful login.
 *
 * @author wangqi
 */
@Schema(name = "LoginResponse", description = "Bearer token returned by a successful login")
public record LoginResponse(

        @Schema(description = "Signed HS256 JWT", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,

        @Schema(description = "Scheme to use in the Authorization header", example = "Bearer")
        String tokenType,

        @Schema(description = "Remaining validity in seconds", example = "3600")
        long expiresInSeconds,

        @Schema(description = "Authenticated username", example = "admin")
        String username
) {
}
