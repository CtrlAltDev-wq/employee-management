package com.wangqi.employee.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Token returned by a successful login.
 *
 * <p>The granted roles travel with the token so the SPA can render the menu and
 * the write actions it is allowed to use. They are a display hint only: every
 * endpoint still authorises the caller on the server.
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
        String username,

        @Schema(description = "Granted authorities, used by the client to hide actions",
                example = "[\"ROLE_ADMIN\"]")
        List<String> roles
) {
}
