package edu.stankin.cogoalmain.web.controllers.internal;

import edu.stankin.cogoalmain.config.OpenApiConfig;
import edu.stankin.cogoalmain.service.UserService;
import edu.stankin.cogoalmain.web.dto.internal.InternalUserRequest;
import edu.stankin.cogoalmain.web.dto.internal.InternalUserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
@Tag(name = "Internal: users", description = "Called by auth-service")
@SecurityRequirement(name = OpenApiConfig.INTERNAL_TOKEN)
public class InternalUserController {

    private final UserService userService;

    @PostMapping
    @Operation(summary = "Create the profile of a newly registered user",
            description = "Idempotent: 201 when created, 200 with the existing profile on a repeated call")
    public ResponseEntity<InternalUserResponse> create(@Valid @RequestBody InternalUserRequest request) {
        UserService.Registration registration = userService.register(request);
        return ResponseEntity
                .status(registration.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(registration.user());
    }
}
