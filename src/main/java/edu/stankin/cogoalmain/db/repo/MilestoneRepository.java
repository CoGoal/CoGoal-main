package edu.stankin.cogoalmain.db.repo;

import edu.stankin.cogoalmain.db.entity.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MilestoneRepository extends JpaRepository<Milestone, UUID> {

    /** Milestone with its goal, which is needed for the owner and status checks. */
    @Query("select m from Milestone m join fetch m.goal where m.id = :id")
    Optional<Milestone> findWithGoalById(@Param("id") UUID id);

    /**
     * Pending milestones of goals being pursued in active pacts whose deadline is within the reminder window
     * and that were not reminded yet. Milestones without a deadline are covered by the goal reminder.
     */
    @Query("""
            select m.id from Milestone m join m.goal g
            where m.status = edu.stankin.cogoalmain.db.entity.enums.MilestoneStatus.PENDING
              and m.reminderSentAt is null
              and m.deadline > :now and m.deadline <= :until
              and exists (select 1 from PactParticipant p
                          where p.goal = g
                            and p.status = edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus.ACTIVE
                            and p.pact.status = edu.stankin.cogoalmain.db.entity.enums.PactStatus.ACTIVE)
            """)
    List<UUID> findIdsToRemind(@Param("now") Instant now, @Param("until") Instant until);

    /** Marks the reminder as sent; 0 if another run already did (so the email is sent once). */
    @Modifying
    @Query("update Milestone m set m.reminderSentAt = :now where m.id = :id and m.reminderSentAt is null")
    int markReminderSent(@Param("id") UUID id, @Param("now") Instant now);

    /** Milestone with its goal and the goal's owner, for the reminder email. */
    @Query("select m from Milestone m join fetch m.goal g join fetch g.user where m.id = :id")
    Optional<Milestone> findWithOwnerById(@Param("id") UUID id);
}
