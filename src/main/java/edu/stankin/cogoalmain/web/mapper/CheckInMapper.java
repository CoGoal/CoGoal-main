package edu.stankin.cogoalmain.web.mapper;

import edu.stankin.cogoalmain.db.entity.CheckIn;
import edu.stankin.cogoalmain.db.entity.Milestone;
import edu.stankin.cogoalmain.db.entity.Proof;
import edu.stankin.cogoalmain.db.entity.Review;
import edu.stankin.cogoalmain.web.dto.checkin.CheckInResponse;
import edu.stankin.cogoalmain.web.dto.checkin.CheckInSummaryResponse;
import edu.stankin.cogoalmain.web.dto.checkin.MilestoneRef;
import edu.stankin.cogoalmain.web.dto.checkin.ProofResponse;
import edu.stankin.cogoalmain.web.dto.checkin.ReviewResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

/**
 * Check-ins, proofs and reviews. The storage key of a file is never exposed; clients get
 * {@link #downloadUrl(Proof) an API path} instead.
 */
@Mapper(config = CentralMapperConfig.class, uses = GoalMapper.class)
public interface CheckInMapper {

    /** Requires {@code participant.user} and {@code milestone} to be loaded. */
    @Mapping(target = "participantId", source = "participant.id")
    @Mapping(target = "author", source = "participant.user")
    @Mapping(target = "finalReport", expression = "java(checkIn.getMilestone() == null)")
    CheckInSummaryResponse toSummary(CheckIn checkIn);

    /** Requires {@code participant.user}, {@code participant.pact}, {@code participant.goal} and {@code milestone}. */
    @Mapping(target = "id", source = "checkIn.id")
    @Mapping(target = "pactId", source = "checkIn.participant.pact.id")
    @Mapping(target = "participantId", source = "checkIn.participant.id")
    @Mapping(target = "author", source = "checkIn.participant.user")
    @Mapping(target = "goalId", source = "checkIn.participant.goal.id")
    @Mapping(target = "milestone", source = "checkIn.milestone")
    @Mapping(target = "finalReport", expression = "java(checkIn.getMilestone() == null)")
    @Mapping(target = "status", source = "checkIn.status")
    @Mapping(target = "comment", source = "checkIn.comment")
    @Mapping(target = "submittedAt", source = "checkIn.submittedAt")
    @Mapping(target = "proofs", source = "proofs")
    @Mapping(target = "reviews", source = "reviews")
    CheckInResponse toResponse(CheckIn checkIn, List<Proof> proofs, List<Review> reviews);

    MilestoneRef toRef(Milestone milestone);

    @Mapping(target = "downloadUrl", source = "proof", qualifiedByName = "downloadUrl")
    ProofResponse toResponse(Proof proof);

    /** Requires {@code reviewer.user} to be loaded. */
    @Mapping(target = "reviewer", source = "reviewer.user")
    ReviewResponse toResponse(Review review);

    @Named("downloadUrl")
    default String downloadUrl(Proof proof) {
        return proof.getFileUrl() == null ? null : "/api/v1/proofs/" + proof.getId() + "/file";
    }
}
