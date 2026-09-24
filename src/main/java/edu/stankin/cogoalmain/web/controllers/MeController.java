package edu.stankin.cogoalmain.web.controllers;

import edu.stankin.cogoalmain.config.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@Tag(name = "Me", description = "Current authenticated user")
public class MeController {

    @GetMapping
    @Operation(summary = "Return the claims of the bearer token the request was made with")
    public AuthenticatedUser me(@AuthenticationPrincipal AuthenticatedUser user) {
        return user;
    }
}
