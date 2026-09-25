package edu.stankin.cogoalmain.support;

import edu.stankin.cogoalmain.db.entity.Category;
import edu.stankin.cogoalmain.db.entity.Charity;
import edu.stankin.cogoalmain.db.entity.Goal;
import edu.stankin.cogoalmain.db.entity.Pact;
import edu.stankin.cogoalmain.db.entity.PactParticipant;
import edu.stankin.cogoalmain.db.entity.User;
import edu.stankin.cogoalmain.db.entity.enums.DepositStatus;
import edu.stankin.cogoalmain.db.entity.enums.GoalStatus;
import edu.stankin.cogoalmain.db.entity.enums.PactStatus;
import edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static edu.stankin.cogoalmain.support.TestEntities.withId;

/**
 * In-memory object graphs of pacts for unit tests.
 */
public final class PactFixtures {

    private PactFixtures() {
    }

    public static User user(String username) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        return user;
    }

    public static Goal goal(User owner, GoalStatus status) {
        Category category = withId(new Category());
        category.setName("Sport");

        Goal goal = withId(new Goal());
        goal.setUser(owner);
        goal.setCategory(category);
        goal.setTitle(owner.getUsername() + "'s goal");
        goal.setDeadline(Instant.now().plus(Duration.ofDays(30)));
        goal.setStatus(status);
        return goal;
    }

    /** An OPEN pact of {@code creator}; the creator's participation is not created. */
    public static Pact pact(User creator, PactStatus status) {
        Charity charity = withId(new Charity());
        charity.setName("Fund");

        Pact pact = withId(new Pact());
        pact.setGoal(goal(creator, GoalStatus.IN_PACT));
        pact.setCharity(charity);
        pact.setStatus(status);
        return pact;
    }

    public static PactParticipant participant(Pact pact, User user, ParticipantStatus status, DepositStatus deposit) {
        Goal goal = pact.getGoal().getUser() == user ? pact.getGoal() : goal(user, GoalStatus.IN_PACT);

        PactParticipant participant = withId(new PactParticipant());
        participant.setPact(pact);
        participant.setUser(user);
        participant.setGoal(goal);
        participant.setStatus(status);
        participant.setDepositAmount(new BigDecimal("1000.00"));
        participant.setDepositStatus(deposit);
        return participant;
    }
}
