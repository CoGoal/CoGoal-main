package edu.stankin.cogoalmain.service;

import edu.stankin.cogoalmain.db.entity.Review;
import edu.stankin.cogoalmain.db.entity.enums.CheckInStatus;
import edu.stankin.cogoalmain.db.entity.enums.ReviewStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * The single place that decides a report's outcome from its reviews.
 * <p>
 * Current rule: the first review decides — APPROVED approves the report, REJECTED (always with a comment)
 * rejects it and the author may submit a new report before the deadline.
 * <p>
 * To change the rule (e.g. a majority of partners), change only {@link #decide}: it receives every review
 * so far and the number of partners who may review, and may return empty to keep the report pending.
 */
@Component
public class ReviewDecisionPolicy {

    /**
     * @param reviews           all reviews of the report, including the one just submitted (last)
     * @param eligibleReviewers active partners in the pact other than the author
     * @return the report's new status, or empty to wait for more reviews
     */
    public Optional<CheckInStatus> decide(List<Review> reviews, long eligibleReviewers) {
        ReviewStatus latest = reviews.get(reviews.size() - 1).getStatus();
        return Optional.of(latest == ReviewStatus.APPROVED ? CheckInStatus.APPROVED : CheckInStatus.REJECTED);
    }
}
