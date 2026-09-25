package edu.stankin.cogoalmain.db.repo;

import edu.stankin.cogoalmain.db.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    boolean existsByCheckInIdAndReviewerId(UUID checkInId, UUID reviewerParticipantId);

    @Query("""
            select r from Review r join fetch r.reviewer rv join fetch rv.user
            where r.checkIn.id = :checkInId
            order by r.createdAt
            """)
    List<Review> findByCheckIn(@Param("checkInId") UUID checkInId);
}
