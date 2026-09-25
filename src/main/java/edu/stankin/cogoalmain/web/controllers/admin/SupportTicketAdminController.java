package edu.stankin.cogoalmain.web.controllers.admin;

import edu.stankin.cogoalmain.db.entity.enums.SupportTicketStatus;
import edu.stankin.cogoalmain.service.SupportService;
import edu.stankin.cogoalmain.web.dto.support.AdminSupportTicketResponse;
import edu.stankin.cogoalmain.web.dto.support.UpdateSupportTicketStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.SortDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/support-tickets")
@RequiredArgsConstructor
@Tag(name = "Admin: support tickets")
public class SupportTicketAdminController {

    private final SupportService supportService;

    @GetMapping
    @Operation(summary = "All tickets, oldest first (the queue), optionally by status")
    public Page<AdminSupportTicketResponse> list(@RequestParam(required = false) SupportTicketStatus status,
                                                 @ParameterObject @SortDefault(sort = "createdAt") Pageable pageable) {
        return supportService.listAll(status, pageable);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Change the ticket status (any status, including reopening)")
    public AdminSupportTicketResponse changeStatus(@PathVariable UUID id,
                                                   @Valid @RequestBody UpdateSupportTicketStatusRequest request) {
        return supportService.changeStatus(id, request.status());
    }
}
