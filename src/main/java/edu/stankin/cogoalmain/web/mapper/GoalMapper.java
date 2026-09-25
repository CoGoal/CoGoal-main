package edu.stankin.cogoalmain.web.mapper;

import edu.stankin.cogoalmain.db.entity.Category;
import edu.stankin.cogoalmain.db.entity.Goal;
import edu.stankin.cogoalmain.db.entity.Milestone;
import edu.stankin.cogoalmain.db.entity.PactParticipant;
import edu.stankin.cogoalmain.db.entity.User;
import edu.stankin.cogoalmain.web.dto.goal.CategoryRef;
import edu.stankin.cogoalmain.web.dto.goal.CreateGoalRequest;
import edu.stankin.cogoalmain.web.dto.goal.CreateMilestoneRequest;
import edu.stankin.cogoalmain.web.dto.goal.FeedItemResponse;
import edu.stankin.cogoalmain.web.dto.goal.GoalResponse;
import edu.stankin.cogoalmain.web.dto.goal.GoalSummaryResponse;
import edu.stankin.cogoalmain.web.dto.goal.MilestoneResponse;
import edu.stankin.cogoalmain.web.dto.goal.UpdateGoalRequest;
import edu.stankin.cogoalmain.web.dto.goal.UpdateMilestoneRequest;
import edu.stankin.cogoalmain.web.dto.goal.UserRef;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Goals and milestones. Owner, category and status are set by the service, never from a request.
 */
@Mapper(config = CentralMapperConfig.class)
public interface GoalMapper {

    /** Requires {@code category} and {@code milestones} to be loaded. */
    @Mapping(target = "ownerId", source = "user.id")
    GoalResponse toResponse(Goal goal);

    /** Requires {@code category} to be loaded. */
    GoalSummaryResponse toSummary(Goal goal);

    MilestoneResponse toResponse(Milestone milestone);

    CategoryRef toRef(Category category);

    UserRef toRef(User user);

    /** Requires {@code goal}, {@code goal.category} and {@code goal.user} to be loaded. */
    @Mapping(target = "owner", source = "goal.user")
    @Mapping(target = "pactId", source = "pact.id")
    FeedItemResponse toFeedItem(PactParticipant participant);

    @Mapping(target = "user", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "milestones", ignore = true)
    Goal toEntity(CreateGoalRequest request);

    /** PATCH; the category is changed by the service because it has to be loaded and checked. */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "milestones", ignore = true)
    void update(UpdateGoalRequest request, @MappingTarget Goal goal);

    @Mapping(target = "goal", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    @Mapping(target = "reminderSentAt", ignore = true)
    Milestone toEntity(CreateMilestoneRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "goal", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    @Mapping(target = "reminderSentAt", ignore = true)
    void update(UpdateMilestoneRequest request, @MappingTarget Milestone milestone);
}
