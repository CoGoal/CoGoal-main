package edu.stankin.cogoalmain.db.repo;

import edu.stankin.cogoalmain.db.entity.PactParticipant;
import edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PactParticipantRepository extends JpaRepository<PactParticipant, UUID> {

    Optional<PactParticipant> findByPactIdAndUserId(UUID pactId, UUID userId);

    boolean existsByPactIdAndUserIdAndStatusNot(UUID pactId, UUID userId, ParticipantStatus status);

    long countByPactIdAndStatus(UUID pactId, ParticipantStatus status);

    boolean existsByPactIdAndStatus(UUID pactId, ParticipantStatus status);

    /**
     * Active participations that need a decision now: the goal deadline or a milestone deadline has passed,
     * or the final report is approved but the goal was not completed yet (safety net).
     */
    @Query("""
            select p.id from PactParticipant p join p.goal g
            where p.status = edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus.ACTIVE
              and p.pact.status = edu.stankin.cogoalmain.db.entity.enums.PactStatus.ACTIVE
              and (g.deadline <= :now
                   or exists (select 1 from Milestone m
                              where m.goal = g
                                and m.status = edu.stankin.cogoalmain.db.entity.enums.MilestoneStatus.PENDING
                                and coalesce(m.deadline, g.deadline) <= :now)
                   or exists (select 1 from CheckIn c
                              where c.participant = p
                                and c.milestone is null
                                and c.status = edu.stankin.cogoalmain.db.entity.enums.CheckInStatus.APPROVED))
            """)
    List<UUID> findIdsNeedingDecision(@Param("now") Instant now);

    /** Active participations whose goal deadline is within the reminder window and not reminded yet. */
    @Query("""
            select p.id from PactParticipant p join p.goal g
            where p.status = edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus.ACTIVE
              and p.pact.status = edu.stankin.cogoalmain.db.entity.enums.PactStatus.ACTIVE
              and p.reminderSentAt is null
              and g.deadline > :now and g.deadline <= :until
            """)
    List<UUID> findIdsToRemind(@Param("now") Instant now, @Param("until") Instant until);

    /** Marks the goal reminder as sent; 0 if another run already did (so the email is sent once). */
    @Modifying
    @Query("update PactParticipant p set p.reminderSentAt = :now where p.id = :id and p.reminderSentAt is null")
    int markReminderSent(@Param("id") UUID id, @Param("now") Instant now);

    /** Participation with its user, goal and pact, for deadline processing and notifications. */
    @Query("""
            select p from PactParticipant p
                join fetch p.user join fetch p.goal join fetch p.pact pact join fetch pact.charity
            where p.id = :id
            """)
    Optional<PactParticipant> findForProcessing(@Param("id") UUID id);

    /**
     * Deposits still held although the participation is over: they should have been charged (FAILED)
     * or released (COMPLETED, LEFT), but payment-service has not confirmed it yet.
     */
    @Query("""
            select p from PactParticipant p join fetch p.pact pact
            where p.depositStatus = edu.stankin.cogoalmain.db.entity.enums.DepositStatus.HELD
              and p.status in (edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus.FAILED,
                               edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus.COMPLETED,
                               edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus.LEFT)
            """)
    List<PactParticipant> findUnsettledDeposits();

    /** All participations in the pact with their users and goals, in joining order. */
    @Query("""
            select p from PactParticipant p
                join fetch p.user
                join fetch p.goal g
                join fetch g.category
            where p.pact.id = :pactId
            order by p.joinedAt
            """)
    List<PactParticipant> findAllInPact(@Param("pactId") UUID pactId);

    /** The user's participations (including ones they left) with pact and charity, for "my pacts". */
    @Query(value = """
            select p from PactParticipant p
                join fetch p.pact pact
                join fetch pact.charity
            where p.user.id = :userId
            """,
            countQuery = "select count(p) from PactParticipant p where p.user.id = :userId")
    Page<PactParticipant> findMine(@Param("userId") UUID userId, Pageable pageable);

    /** Only the pact id, so the pact can be locked before the participation itself is loaded. */
    @Query("select p.pact.id from PactParticipant p where p.id = :id")
    Optional<UUID> findPactIdById(@Param("id") UUID id);

    /**
     * Removes the history of pacts the goal was withdrawn from, so a DRAFT goal can be deleted
     * ({@code pact_participant.goal_id} is {@code ON DELETE RESTRICT}).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PactParticipant p where p.goal.id = :goalId"
            + " and p.status = edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus.LEFT")
    int deleteLeftByGoalId(@Param("goalId") UUID goalId);

    /**
     * Feed: other users' goals that are in a pact still recruiting participants.
     * One row per goal (a goal has at most one non-LEFT participation).
     */
    @Query(value = """
            select p from PactParticipant p
                join fetch p.goal g
                join fetch g.category
                join fetch g.user
                join p.pact pact
            where g.status = edu.stankin.cogoalmain.db.entity.enums.GoalStatus.IN_PACT
              and p.status <> edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus.LEFT
              and pact.status = edu.stankin.cogoalmain.db.entity.enums.PactStatus.OPEN
              and g.user.id <> :userId
            """,
            countQuery = """
            select count(p) from PactParticipant p
                join p.goal g
                join p.pact pact
            where g.status = edu.stankin.cogoalmain.db.entity.enums.GoalStatus.IN_PACT
              and p.status <> edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus.LEFT
              and pact.status = edu.stankin.cogoalmain.db.entity.enums.PactStatus.OPEN
              and g.user.id <> :userId
            """)
    Page<PactParticipant> findFeed(@Param("userId") UUID userId, Pageable pageable);

    /** {@link #findFeed} limited to one category. */
    @Query(value = """
            select p from PactParticipant p
                join fetch p.goal g
                join fetch g.category
                join fetch g.user
                join p.pact pact
            where g.status = edu.stankin.cogoalmain.db.entity.enums.GoalStatus.IN_PACT
              and p.status <> edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus.LEFT
              and pact.status = edu.stankin.cogoalmain.db.entity.enums.PactStatus.OPEN
              and g.user.id <> :userId
              and g.category.id = :categoryId
            """,
            countQuery = """
            select count(p) from PactParticipant p
                join p.goal g
                join p.pact pact
            where g.status = edu.stankin.cogoalmain.db.entity.enums.GoalStatus.IN_PACT
              and p.status <> edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus.LEFT
              and pact.status = edu.stankin.cogoalmain.db.entity.enums.PactStatus.OPEN
              and g.user.id <> :userId
              and g.category.id = :categoryId
            """)
    Page<PactParticipant> findFeedByCategory(@Param("userId") UUID userId,
                                             @Param("categoryId") UUID categoryId,
                                             Pageable pageable);
}
