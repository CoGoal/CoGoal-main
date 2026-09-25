package edu.stankin.cogoalmain.db.repo;

import edu.stankin.cogoalmain.db.entity.CheckIn;
import edu.stankin.cogoalmain.db.entity.enums.CheckInStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface CheckInRepository extends JpaRepository<CheckIn, UUID> {

    boolean existsByParticipantIdAndMilestoneIdAndStatusIn(UUID participantId, UUID milestoneId,
                                                           Collection<CheckInStatus> statuses);

    /** Final reports (on the whole goal) have no milestone. */
    boolean existsByParticipantIdAndMilestoneIsNullAndStatusIn(UUID participantId, Collection<CheckInStatus> statuses);

    /** Only the pact id, so the pact can be locked before the check-in itself is loaded. */
    @Query("select c.participant.pact.id from CheckIn c where c.id = :id")
    Optional<UUID> findPactIdById(@Param("id") UUID id);

    /**
     * Check-in with author, pact, goal and milestone. Proofs and reviews are loaded separately
     * (fetching two collections at once would multiply rows).
     */
    @EntityGraph(attributePaths = {"participant", "participant.user", "participant.pact", "participant.goal", "milestone"})
    Optional<CheckIn> findDetailedById(UUID id);

    @Query(value = """
            select c from CheckIn c
                join fetch c.participant p
                join fetch p.user
                left join fetch c.milestone
            where p.pact.id = :pactId
            """,
            countQuery = "select count(c) from CheckIn c where c.participant.pact.id = :pactId")
    Page<CheckIn> findByPact(@Param("pactId") UUID pactId, Pageable pageable);

    /**
     * Pending reports the user may review: in active pacts where the user is an active participant,
     * written by someone else, and not reviewed by the user yet.
     */
    @Query(value = """
            select c from CheckIn c
                join fetch c.participant p
                join fetch p.user
                left join fetch c.milestone
            where c.status = edu.stankin.cogoalmain.db.entity.enums.CheckInStatus.PENDING
              and p.user.id <> :userId
              and exists (
                  select 1 from PactParticipant me
                  where me.pact = p.pact
                    and me.user.id = :userId
                    and me.status = edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus.ACTIVE
                    and me.pact.status = edu.stankin.cogoalmain.db.entity.enums.PactStatus.ACTIVE)
              and not exists (
                  select 1 from Review r where r.checkIn = c and r.reviewer.user.id = :userId)
            """,
            countQuery = """
            select count(c) from CheckIn c join c.participant p
            where c.status = edu.stankin.cogoalmain.db.entity.enums.CheckInStatus.PENDING
              and p.user.id <> :userId
              and exists (
                  select 1 from PactParticipant me
                  where me.pact = p.pact
                    and me.user.id = :userId
                    and me.status = edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus.ACTIVE
                    and me.pact.status = edu.stankin.cogoalmain.db.entity.enums.PactStatus.ACTIVE)
              and not exists (
                  select 1 from Review r where r.checkIn = c and r.reviewer.user.id = :userId)
            """)
    Page<CheckIn> findToReview(@Param("userId") UUID userId, Pageable pageable);
}
