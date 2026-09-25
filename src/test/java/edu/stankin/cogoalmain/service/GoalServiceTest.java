package edu.stankin.cogoalmain.service;

import edu.stankin.cogoalmain.db.entity.Category;
import edu.stankin.cogoalmain.db.entity.Goal;
import edu.stankin.cogoalmain.db.entity.Milestone;
import edu.stankin.cogoalmain.db.entity.User;
import edu.stankin.cogoalmain.db.entity.enums.GoalStatus;
import edu.stankin.cogoalmain.db.entity.enums.MilestoneStatus;
import edu.stankin.cogoalmain.db.repo.GoalRepository;
import edu.stankin.cogoalmain.db.repo.MilestoneRepository;
import edu.stankin.cogoalmain.db.repo.PactParticipantRepository;
import edu.stankin.cogoalmain.db.repo.UserRepository;
import edu.stankin.cogoalmain.exception.BusinessRuleException;
import edu.stankin.cogoalmain.exception.ForbiddenException;
import edu.stankin.cogoalmain.web.dto.goal.CreateGoalRequest;
import edu.stankin.cogoalmain.web.dto.goal.CreateMilestoneRequest;
import edu.stankin.cogoalmain.web.dto.goal.GoalResponse;
import edu.stankin.cogoalmain.web.dto.goal.UpdateGoalRequest;
import edu.stankin.cogoalmain.web.dto.goal.UpdateMilestoneRequest;
import edu.stankin.cogoalmain.web.mapper.GoalMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static edu.stankin.cogoalmain.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID OTHER_ID = UUID.randomUUID();
    private static final Instant IN_30_DAYS = Instant.now().plus(Duration.ofDays(30));

    @Mock
    private GoalRepository goalRepository;
    @Mock
    private MilestoneRepository milestoneRepository;
    @Mock
    private PactParticipantRepository participantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CategoryService categoryService;

    private GoalService service;

    @BeforeEach
    void setUp() {
        service = new GoalService(goalRepository, milestoneRepository, participantRepository, userRepository,
                categoryService, Mappers.getMapper(GoalMapper.class));
    }

    @Test
    void newGoalIsDraftOfCurrentUser() {
        Category category = withId(new Category());
        given(userRepository.findById(OWNER_ID)).willReturn(Optional.of(owner()));
        given(categoryService.requireActive(category.getId())).willReturn(category);
        given(goalRepository.save(any())).willAnswer(invocation -> withId(invocation.<Goal>getArgument(0)));

        GoalResponse response = service.create(OWNER_ID,
                new CreateGoalRequest(category.getId(), "Run a marathon", null, IN_30_DAYS));

        assertThat(response.status()).isEqualTo(GoalStatus.DRAFT);
        assertThat(response.ownerId()).isEqualTo(OWNER_ID);
        assertThat(response.category().id()).isEqualTo(category.getId());
    }

    @Nested
    class Visibility {

        @Test
        void ownerSeesOwnDraft() {
            Goal goal = goal(GoalStatus.DRAFT);
            given(goalRepository.findDetailedById(goal.getId())).willReturn(Optional.of(goal));

            assertThat(service.get(OWNER_ID, goal.getId()).id()).isEqualTo(goal.getId());
        }

        @Test
        void othersCannotSeeDraft() {
            Goal goal = goal(GoalStatus.DRAFT);
            given(goalRepository.findDetailedById(goal.getId())).willReturn(Optional.of(goal));

            assertThatThrownBy(() -> service.get(OTHER_ID, goal.getId())).isInstanceOf(ForbiddenException.class);
        }

        @Test
        void othersSeeGoalPublishedInPact() {
            Goal goal = goal(GoalStatus.IN_PACT);
            given(goalRepository.findDetailedById(goal.getId())).willReturn(Optional.of(goal));

            assertThat(service.get(OTHER_ID, goal.getId()).status()).isEqualTo(GoalStatus.IN_PACT);
        }
    }

    @Nested
    class Editing {

        @Test
        void onlyOwnerCanEdit() {
            Goal goal = goal(GoalStatus.DRAFT);
            given(goalRepository.findDetailedById(goal.getId())).willReturn(Optional.of(goal));

            assertThatThrownBy(() -> service.update(OTHER_ID, goal.getId(), titleOnly("Hacked")))
                    .isInstanceOf(ForbiddenException.class);
            assertThat(goal.getTitle()).isEqualTo("Run a marathon");
        }

        @Test
        void goalInPactCannotBeEdited() {
            Goal goal = goal(GoalStatus.IN_PACT);
            given(goalRepository.findDetailedById(goal.getId())).willReturn(Optional.of(goal));

            assertThatThrownBy(() -> service.update(OWNER_ID, goal.getId(), titleOnly("New")))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("DRAFT");
        }

        @Test
        void patchChangesOnlyGivenFields() {
            Goal goal = goal(GoalStatus.DRAFT);
            goal.setDescription("42 km");
            given(goalRepository.findDetailedById(goal.getId())).willReturn(Optional.of(goal));

            service.update(OWNER_ID, goal.getId(), titleOnly("Run a half marathon"));

            assertThat(goal.getTitle()).isEqualTo("Run a half marathon");
            assertThat(goal.getDescription()).isEqualTo("42 km");
            assertThat(goal.getDeadline()).isEqualTo(IN_30_DAYS);
        }

        @Test
        void goalDeadlineCannotMoveBeforeItsMilestones() {
            Goal goal = goal(GoalStatus.DRAFT);
            goal.getMilestones().add(milestone(goal, IN_30_DAYS.minus(Duration.ofDays(5))));
            given(goalRepository.findDetailedById(goal.getId())).willReturn(Optional.of(goal));

            assertThatThrownBy(() -> service.update(OWNER_ID, goal.getId(),
                    new UpdateGoalRequest(null, null, null, IN_30_DAYS.minus(Duration.ofDays(10)))))
                    .isInstanceOf(BusinessRuleException.class);
            assertThat(goal.getDeadline()).isEqualTo(IN_30_DAYS);
        }
    }

    @Nested
    class Deletion {

        @Test
        void removesLeftParticipationsBeforeDeletingGoal() {
            Goal goal = goal(GoalStatus.DRAFT);
            given(goalRepository.findDetailedById(goal.getId())).willReturn(Optional.of(goal));

            service.delete(OWNER_ID, goal.getId());

            InOrder order = inOrder(participantRepository, goalRepository);
            order.verify(participantRepository).deleteLeftByGoalId(goal.getId());
            order.verify(goalRepository).deleteById(goal.getId());
        }

        @Test
        void goalInPactCannotBeDeleted() {
            Goal goal = goal(GoalStatus.IN_PACT);
            given(goalRepository.findDetailedById(goal.getId())).willReturn(Optional.of(goal));

            assertThatThrownBy(() -> service.delete(OWNER_ID, goal.getId()))
                    .isInstanceOf(BusinessRuleException.class);
            verify(goalRepository, never()).deleteById(any());
        }
    }

    @Nested
    class Milestones {

        @Test
        void milestoneIsPendingAndBelongsToGoal() {
            Goal goal = goal(GoalStatus.DRAFT);
            given(goalRepository.findDetailedById(goal.getId())).willReturn(Optional.of(goal));
            given(milestoneRepository.save(any())).willAnswer(invocation -> withId(invocation.<Milestone>getArgument(0)));

            var response = service.addMilestone(OWNER_ID, goal.getId(),
                    new CreateMilestoneRequest("First 10 km", null, IN_30_DAYS.minus(Duration.ofDays(20))));

            assertThat(response.status()).isEqualTo(MilestoneStatus.PENDING);
        }

        @Test
        void milestoneDeadlineCannotBeAfterGoalDeadline() {
            Goal goal = goal(GoalStatus.DRAFT);
            given(goalRepository.findDetailedById(goal.getId())).willReturn(Optional.of(goal));

            assertThatThrownBy(() -> service.addMilestone(OWNER_ID, goal.getId(),
                    new CreateMilestoneRequest("Too late", null, IN_30_DAYS.plusSeconds(1))))
                    .isInstanceOf(BusinessRuleException.class);
            verify(milestoneRepository, never()).save(any());
        }

        @Test
        void milestoneOfGoalInPactCannotBeChanged() {
            Goal goal = goal(GoalStatus.IN_PACT);
            Milestone milestone = milestone(goal, IN_30_DAYS.minus(Duration.ofDays(5)));
            given(milestoneRepository.findWithGoalById(milestone.getId())).willReturn(Optional.of(milestone));

            assertThatThrownBy(() -> service.updateMilestone(OWNER_ID, milestone.getId(),
                    new UpdateMilestoneRequest("New", null, null)))
                    .isInstanceOf(BusinessRuleException.class);
            assertThatThrownBy(() -> service.deleteMilestone(OWNER_ID, milestone.getId()))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        void othersCannotChangeMilestones() {
            Milestone milestone = milestone(goal(GoalStatus.DRAFT), IN_30_DAYS.minus(Duration.ofDays(5)));
            given(milestoneRepository.findWithGoalById(milestone.getId())).willReturn(Optional.of(milestone));

            assertThatThrownBy(() -> service.deleteMilestone(OTHER_ID, milestone.getId()))
                    .isInstanceOf(ForbiddenException.class);
            verify(milestoneRepository, never()).delete(any());
        }
    }

    private static UpdateGoalRequest titleOnly(String title) {
        return new UpdateGoalRequest(null, title, null, null);
    }

    private static User owner() {
        User user = new User();
        user.setId(OWNER_ID);
        user.setUsername("owner");
        return user;
    }

    private static Goal goal(GoalStatus status) {
        Category category = withId(new Category());
        category.setName("Sport");

        Goal goal = withId(new Goal());
        goal.setUser(owner());
        goal.setCategory(category);
        goal.setTitle("Run a marathon");
        goal.setDeadline(IN_30_DAYS);
        goal.setStatus(status);
        return goal;
    }

    private static Milestone milestone(Goal goal, Instant deadline) {
        Milestone milestone = withId(new Milestone());
        milestone.setGoal(goal);
        milestone.setTitle("Milestone");
        milestone.setDeadline(deadline);
        milestone.setStatus(MilestoneStatus.PENDING);
        return milestone;
    }
}
