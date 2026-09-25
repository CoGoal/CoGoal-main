package edu.stankin.cogoalmain.service;

import edu.stankin.cogoalmain.db.entity.SupportTicket;
import edu.stankin.cogoalmain.db.entity.enums.SupportTicketStatus;
import edu.stankin.cogoalmain.db.repo.SupportTicketRepository;
import edu.stankin.cogoalmain.db.repo.UserRepository;
import edu.stankin.cogoalmain.exception.ForbiddenException;
import edu.stankin.cogoalmain.exception.NotFoundException;
import edu.stankin.cogoalmain.web.dto.support.AdminSupportTicketResponse;
import edu.stankin.cogoalmain.web.dto.support.CreateSupportTicketRequest;
import edu.stankin.cogoalmain.web.dto.support.SupportTicketResponse;
import edu.stankin.cogoalmain.web.mapper.SupportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Support tickets: users open them and see only their own; admins see all and change their status.
 */
@Service
@RequiredArgsConstructor
public class SupportService {

    private final SupportTicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final SupportMapper supportMapper;

    @Transactional
    public SupportTicketResponse create(UUID userId, CreateSupportTicketRequest request) {
        SupportTicket ticket = supportMapper.toEntity(request);
        ticket.setUser(userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Profile of user " + userId + " has not been created yet")));
        ticket.setStatus(SupportTicketStatus.OPEN);
        return supportMapper.toResponse(ticketRepository.save(ticket));
    }

    @Transactional(readOnly = true)
    public Page<SupportTicketResponse> getMyTickets(UUID userId, Pageable pageable) {
        return ticketRepository.findByUserId(userId, pageable).map(supportMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public SupportTicketResponse getMyTicket(UUID userId, UUID ticketId) {
        SupportTicket ticket = find(ticketId);
        if (!ticket.getUser().getId().equals(userId)) {
            throw new ForbiddenException("This is another user's ticket");
        }
        return supportMapper.toResponse(ticket);
    }

    @Transactional(readOnly = true)
    public Page<AdminSupportTicketResponse> listAll(SupportTicketStatus status, Pageable pageable) {
        Page<SupportTicket> tickets = status == null
                ? ticketRepository.findAllBy(pageable)
                : ticketRepository.findByStatus(status, pageable);
        return tickets.map(supportMapper::toAdminResponse);
    }

    /** Any status may be set, including reopening a closed ticket. */
    @Transactional
    public AdminSupportTicketResponse changeStatus(UUID ticketId, SupportTicketStatus status) {
        SupportTicket ticket = find(ticketId);
        ticket.setStatus(status);
        // Flush so the response carries the new updatedAt (set by Hibernate when the row is written)
        return supportMapper.toAdminResponse(ticketRepository.saveAndFlush(ticket));
    }

    private SupportTicket find(UUID ticketId) {
        return ticketRepository.findWithUserById(ticketId)
                .orElseThrow(() -> NotFoundException.of("Support ticket", ticketId));
    }
}
