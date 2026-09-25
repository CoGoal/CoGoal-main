package edu.stankin.cogoalmain.web.controllers;

import edu.stankin.cogoalmain.config.security.CurrentUserId;
import edu.stankin.cogoalmain.service.MessageService;
import edu.stankin.cogoalmain.web.dto.pact.MessagePageResponse;
import edu.stankin.cogoalmain.web.dto.pact.MessageResponse;
import edu.stankin.cogoalmain.web.dto.pact.SendMessageRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pacts/{id}/messages")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Pact chat for participants who have not left")
public class MessageController {

    private final MessageService messageService;

    @GetMapping
    @Operation(summary = "Messages newest first; pass nextBefore from the response as 'before' for older ones")
    public MessagePageResponse list(@CurrentUserId UUID userId, @PathVariable UUID id,
                                    @RequestParam(required = false) Instant before,
                                    @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return messageService.list(userId, id, before, limit);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse send(@CurrentUserId UUID userId, @PathVariable UUID id,
                                @Valid @RequestBody SendMessageRequest request) {
        return messageService.send(userId, id, request.text());
    }
}
