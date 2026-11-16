package com.wangqi.employee.controller;

import com.wangqi.employee.common.Result;
import com.wangqi.employee.dto.LoginRequest;
import com.wangqi.employee.dto.LoginResponse;
import com.wangqi.employee.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication entry point.
 *
 * @author wangqi
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Exchange credentials for a JWT used by every other endpoint")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    /**
     * {@code POST /api/auth/login} - exchanges username/password for a JWT.
     * Invalid credentials are translated into a 401 by the global exception handler.
     */
    @Operation(
            summary = "Log in",
            description = "Returns an HS256 bearer token. Send it as 'Authorization: Bearer <token>' on every other call.")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated; the token is in data.accessToken"),
            @ApiResponse(responseCode = "400", description = "Username or password missing"),
            @ApiResponse(responseCode = "401", description = "Invalid username or password (message is intentionally generic)")
    })
    @PostMapping("/login")
    public ResponseEntity<Result<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String token = jwtService.generateToken(userDetails);

        return ResponseEntity.ok(Result.ok("Login successful.", new LoginResponse(
                token,
                "Bearer",
                jwtService.expirationSeconds(),
                userDetails.getUsername()
        )));
    }
}
