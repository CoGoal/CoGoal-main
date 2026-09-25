package edu.stankin.cogoalmain.web.controllers;

import edu.stankin.cogoalmain.config.security.CurrentUserId;
import edu.stankin.cogoalmain.service.SupportService;
import edu.stankin.cogoalmain.web.dto.support.CreateSupportTicketRequest;
import edu.stankin.cogoalmain.web.dto.support.SupportTicketResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/support/tickets")
@RequiredArgsConstructor
@Tag(name = "Support", description = "Own support tickets")
public class SupportController {

    private final SupportService supportService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupportTicketResponse create(@CurrentUserId UUID userId, @Valid @RequestBody CreateSupportTicketRequest request) {
        return supportService.create(userId, request);
    }

    @GetMapping("/my")
    @Operation(summary = "Own tickets, newest first")
    public Page<SupportTicketResponse> my(
            @CurrentUserId UUID userId,
            @ParameterObject @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return supportService.getMyTickets(userId, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One of the own tickets")
    public SupportTicketResponse get(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return supportService.getMyTicket(userId, id);
    }
}
