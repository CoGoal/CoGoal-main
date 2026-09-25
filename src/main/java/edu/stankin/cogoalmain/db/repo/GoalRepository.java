package edu.stankin.cogoalmain.db.repo;

import edu.stankin.cogoalmain.db.entity.Goal;
import edu.stankin.cogoalmain.db.entity.enums.GoalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

    boolean existsByCategoryId(UUID categoryId);

    /** Goal with its category and milestones, for the detail view and for editing. */
    @EntityGraph(attributePaths = {"category", "milestones"})
    Optional<Goal> findDetailedById(UUID id);

    @EntityGraph(attributePaths = "category")
    Page<Goal> findByUserId(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Page<Goal> findByUserIdAndStatus(UUID userId, GoalStatus status, Pageable pageable);
}
