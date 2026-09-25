package edu.stankin.cogoalmain.db.repo;

import edu.stankin.cogoalmain.db.entity.PactInvitation;
import edu.stankin.cogoalmain.db.entity.enums.InvitationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PactInvitationRepository extends JpaRepository<PactInvitation, UUID> {

    /** Only the pact id, so the pact can be locked before the invitation itself is loaded. */
    @Query("select i.pact.id from PactInvitation i where i.id = :id")
    Optional<UUID> findPactIdById(@Param("id") UUID id);

    boolean existsByPactIdAndInviteeIdAndStatus(UUID pactId, UUID inviteeId, InvitationStatus status);

    Optional<PactInvitation> findByPactIdAndInviteeIdAndStatus(UUID pactId, UUID inviteeId, InvitationStatus status);

    @EntityGraph(attributePaths = {"pact", "pact.goal", "inviter", "invitee"})
    Optional<PactInvitation> findDetailedById(UUID id);

    @EntityGraph(attributePaths = {"pact", "pact.goal", "inviter", "invitee"})
    Page<PactInvitation> findByInviteeId(UUID inviteeId, Pageable pageable);

    @EntityGraph(attributePaths = {"pact", "pact.goal", "inviter", "invitee"})
    Page<PactInvitation> findByInviteeIdAndStatus(UUID inviteeId, InvitationStatus status, Pageable pageable);

    /** Cancels all pending invitations of a pact that stops recruiting (started or cancelled). */
    @Modifying(flushAutomatically = true)
    @Query("""
            update PactInvitation i
            set i.status = edu.stankin.cogoalmain.db.entity.enums.InvitationStatus.CANCELLED, i.respondedAt = :now
            where i.pact.id = :pactId
              and i.status = edu.stankin.cogoalmain.db.entity.enums.InvitationStatus.PENDING
            """)
    int cancelPending(@Param("pactId") UUID pactId, @Param("now") Instant now);
}
