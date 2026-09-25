package edu.stankin.cogoalmain.service;

import edu.stankin.cogoalmain.db.entity.Goal;
import edu.stankin.cogoalmain.db.entity.Milestone;
import edu.stankin.cogoalmain.db.entity.enums.GoalStatus;
import edu.stankin.cogoalmain.db.entity.enums.MilestoneStatus;
import edu.stankin.cogoalmain.db.repo.GoalRepository;
import edu.stankin.cogoalmain.db.repo.MilestoneRepository;
import edu.stankin.cogoalmain.db.repo.PactParticipantRepository;
import edu.stankin.cogoalmain.db.repo.UserRepository;
import edu.stankin.cogoalmain.exception.BusinessRuleException;
import edu.stankin.cogoalmain.exception.ForbiddenException;
import edu.stankin.cogoalmain.exception.NotFoundException;
import edu.stankin.cogoalmain.web.dto.goal.CreateGoalRequest;
import edu.stankin.cogoalmain.web.dto.goal.CreateMilestoneRequest;
import edu.stankin.cogoalmain.web.dto.goal.FeedItemResponse;
import edu.stankin.cogoalmain.web.dto.goal.GoalResponse;
import edu.stankin.cogoalmain.web.dto.goal.GoalSummaryResponse;
import edu.stankin.cogoalmain.web.dto.goal.MilestoneResponse;
import edu.stankin.cogoalmain.web.dto.goal.UpdateGoalRequest;
import edu.stankin.cogoalmain.web.dto.goal.UpdateMilestoneRequest;
import edu.stankin.cogoalmain.web.mapper.GoalMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Goals and their milestones.
 * <p>
 * Rules: a goal is created in DRAFT and only its owner may change it, and only while it is in DRAFT
 * (after that it belongs to a pact). Deadlines are in the future (checked on the request DTOs);
 * a milestone's deadline is not later than its goal's deadline.
 */
@Service
@RequiredArgsConstructor
public class GoalService {

    private final GoalRepository goalRepository;
    private final MilestoneRepository milestoneRepository;
    private final PactParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final CategoryService categoryService;
    private final GoalMapper goalMapper;

    @Transactional
    public GoalResponse create(UUID userId, CreateGoalRequest request) {
        Goal goal = goalMapper.toEntity(request);
        goal.setUser(userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Profile of user " + userId + " has not been created yet")));
        goal.setCategory(categoryService.requireActive(request.categoryId()));
        goal.setStatus(GoalStatus.DRAFT);
        return goalMapper.toResponse(goalRepository.save(goal));
    }

    @Transactional(readOnly = true)
    public Page<GoalSummaryResponse> getMyGoals(UUID userId, GoalStatus status, Pageable pageable) {
        Page<Goal> goals = status == null
                ? goalRepository.findByUserId(userId, pageable)
                : goalRepository.findByUserIdAndStatus(userId, status, pageable);
        return goals.map(goalMapper::toSummary);
    }

    /**
     * Own goals are always visible. Other users' goals are visible once published in a pact;
     * drafts stay private.
     */
    @Transactional(readOnly = true)
    public GoalResponse get(UUID userId, UUID goalId) {
        Goal goal = findDetailed(goalId);
        if (!isOwner(goal, userId) && goal.getStatus() == GoalStatus.DRAFT) {
            throw new ForbiddenException("This goal is a private draft");
        }
        return goalMapper.toResponse(goal);
    }

    @Transactional
    public GoalResponse update(UUID userId, UUID goalId, UpdateGoalRequest request) {
        Goal goal = findEditable(userId, goalId);

        if (request.deadline() != null) {
            goal.getMilestones().stream()
                    .map(Milestone::getDeadline)
                    .filter(deadline -> deadline != null && deadline.isAfter(request.deadline()))
                    .findAny()
                    .ifPresent(deadline -> {
                        throw new BusinessRuleException(
                                "Goal deadline cannot be earlier than the deadline of its milestone (" + deadline + ")");
                    });
        }
        if (request.categoryId() != null) {
            goal.setCategory(categoryService.requireActive(request.categoryId()));
        }
        goalMapper.update(request, goal);
        return goalMapper.toResponse(goal);
    }

    /**
     * Deletes a DRAFT goal with its milestones. Records of pacts the goal was withdrawn from (LEFT)
     * are removed first because they still reference the goal.
     */
    @Transactional
    public void delete(UUID userId, UUID goalId) {
        Goal goal = findEditable(userId, goalId);
        participantRepository.deleteLeftByGoalId(goal.getId());
        goalRepository.deleteById(goal.getId());
    }

    /** Other users' goals in pacts that are still recruiting, optionally in one category. */
    @Transactional(readOnly = true)
    public Page<FeedItemResponse> feed(UUID userId, UUID categoryId, Pageable pageable) {
        return (categoryId == null
                ? participantRepository.findFeed(userId, pageable)
                : participantRepository.findFeedByCategory(userId, categoryId, pageable))
                .map(goalMapper::toFeedItem);
    }

    @Transactional
    public MilestoneResponse addMilestone(UUID userId, UUID goalId, CreateMilestoneRequest request) {
        Goal goal = findEditable(userId, goalId);
        requireWithinGoalDeadline(request.deadline(), goal);

        Milestone milestone = goalMapper.toEntity(request);
        milestone.setGoal(goal);
        milestone.setStatus(MilestoneStatus.PENDING);
        return goalMapper.toResponse(milestoneRepository.save(milestone));
    }

    @Transactional
    public MilestoneResponse updateMilestone(UUID userId, UUID milestoneId, UpdateMilestoneRequest request) {
        Milestone milestone = findEditableMilestone(userId, milestoneId);
        if (request.deadline() != null) {
            requireWithinGoalDeadline(request.deadline(), milestone.getGoal());
        }
        goalMapper.update(request, milestone);
        return goalMapper.toResponse(milestone);
    }

    @Transactional
    public void deleteMilestone(UUID userId, UUID milestoneId) {
        milestoneRepository.delete(findEditableMilestone(userId, milestoneId));
    }

    /**
     * The user's own DRAFT goal, e.g. to put it into a pact.
     *
     * @throws NotFoundException     if the goal does not exist
     * @throws ForbiddenException    if it belongs to someone else
     * @throws BusinessRuleException if it is not in DRAFT
     */
    @Transactional(readOnly = true)
    public Goal requireOwnDraft(UUID userId, UUID goalId) {
        return findEditable(userId, goalId);
    }

    private Goal findEditable(UUID userId, UUID goalId) {
        Goal goal = findDetailed(goalId);
        requireOwner(goal, userId);
        requireDraft(goal);
        return goal;
    }

    private Milestone findEditableMilestone(UUID userId, UUID milestoneId) {
        Milestone milestone = milestoneRepository.findWithGoalById(milestoneId)
                .orElseThrow(() -> NotFoundException.of("Milestone", milestoneId));
        requireOwner(milestone.getGoal(), userId);
        requireDraft(milestone.getGoal());
        return milestone;
    }

    private Goal findDetailed(UUID goalId) {
        return goalRepository.findDetailedById(goalId).orElseThrow(() -> NotFoundException.of("Goal", goalId));
    }

    private static boolean isOwner(Goal goal, UUID userId) {
        return goal.getUser().getId().equals(userId);
    }

    private static void requireOwner(Goal goal, UUID userId) {
        if (!isOwner(goal, userId)) {
            throw new ForbiddenException("Not your goal");
        }
    }

    private static void requireDraft(Goal goal) {
        if (goal.getStatus() != GoalStatus.DRAFT) {
            throw new BusinessRuleException("Goal can only be changed while it is a DRAFT (current status: "
                    + goal.getStatus() + ")");
        }
    }

    private static void requireWithinGoalDeadline(Instant milestoneDeadline, Goal goal) {
        if (milestoneDeadline.isAfter(goal.getDeadline())) {
            throw new BusinessRuleException("Milestone deadline cannot be later than the goal deadline ("
                    + goal.getDeadline() + ")");
        }
    }
}
