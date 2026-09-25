package edu.stankin.cogoalmain.db.repo;

import edu.stankin.cogoalmain.db.entity.SupportTicket;
import edu.stankin.cogoalmain.db.entity.enums.SupportTicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {

    Page<SupportTicket> findByUserId(UUID userId, Pageable pageable);

    /** Tickets with their authors, for the admin list. */
    @EntityGraph(attributePaths = "user")
    Page<SupportTicket> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<SupportTicket> findByStatus(SupportTicketStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Optional<SupportTicket> findWithUserById(UUID id);
}
