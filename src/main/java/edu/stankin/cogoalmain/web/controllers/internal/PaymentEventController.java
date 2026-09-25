package edu.stankin.cogoalmain.web.controllers.internal;

import edu.stankin.cogoalmain.config.OpenApiConfig;
import edu.stankin.cogoalmain.service.DepositService;
import edu.stankin.cogoalmain.web.dto.internal.PaymentEventRequest;
import edu.stankin.cogoalmain.web.dto.pact.DepositResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/payments/events")
@RequiredArgsConstructor
@Tag(name = "Internal: payments", description = "Called by payment-service")
@SecurityRequirement(name = OpenApiConfig.INTERNAL_TOKEN)
public class PaymentEventController {

    private final DepositService depositService;

    @PostMapping
    @Operation(summary = "Report the outcome of a deposit operation",
            description = "Idempotent. HELD activates a participant waiting in an open pact; "
                    + "events that would leave a final state (CHARGED, RELEASED) are ignored")
    public DepositResponse onEvent(@Valid @RequestBody PaymentEventRequest request) {
        return depositService.handleEvent(request);
    }
}
