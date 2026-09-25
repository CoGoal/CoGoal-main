package edu.stankin.cogoalmain;

import com.jayway.jsonpath.JsonPath;
import edu.stankin.cogoalmain.client.email.EmailClient;
import edu.stankin.cogoalmain.client.payment.PaymentClient;
import edu.stankin.cogoalmain.config.security.InternalTokenFilter;
import edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus;
import edu.stankin.cogoalmain.service.DeadlineService;
import edu.stankin.cogoalmain.support.PostgresIntegrationTest;
import edu.stankin.cogoalmain.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * The main scenario end to end, over HTTP with real tokens and a real PostgreSQL:
 * create a goal → pact → a second user joins → both deposits HELD → start → reports → review →
 * the goal is completed and coins credited; the partner misses the deadline, the pact finishes,
 * and the coins are spent in the shop.
 * <p>
 * Only the external services (payment, email) are mocked; their calls are verified after each commit.
 */
@AutoConfigureMockMvc
class PactLifecycleIntegrationTest extends PostgresIntegrationTest {

    private static final int GOAL_COINS = 100;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private DeadlineService deadlineService;

    @MockitoBean
    private PaymentClient paymentClient;
    @MockitoBean
    private EmailClient emailClient;

    // Random names, so the test can be rerun against the same external database
    private final String run = UUID.randomUUID().toString().substring(0, 8);
    private final UUID adminId = UUID.randomUUID();
    private final UUID aliceId = UUID.randomUUID();
    private final UUID bobId = UUID.randomUUID();

    @Test
    void goalIsCompletedInAPactAndTheRewardIsSpent() throws Exception {
        given(paymentClient.holdDeposit(any(), any(), any(), any())).willReturn("https://pay.test/deposit");
        Instant now = Instant.now();

        // --- catalog (admin) and users (auth-service)
        String categoryId = id(call(HttpStatus.CREATED, post("/api/v1/admin/categories"), admin(),
                Map.of("name", "Sport " + run)));
        String charityId = id(call(HttpStatus.CREATED, post("/api/v1/admin/charities"), admin(),
                Map.of("name", "Fund " + run)));
        register(aliceId, "alice");
        register(bobId, "bob");
        // Registration is idempotent: a retry by auth-service returns the existing profile
        callInternal(HttpStatus.OK, post("/internal/users"),
                Map.of("id", aliceId, "email", "alice-" + run + "@example.com", "username", "alice"));

        // --- Alice: goal with one milestone, published as a pact
        String aliceGoal = id(call(HttpStatus.CREATED, post("/api/v1/goals"), alice(), Map.of(
                "categoryId", categoryId, "title", "Run a marathon", "deadline", now.plus(Duration.ofDays(30)))));
        String milestoneId = id(call(HttpStatus.CREATED, post("/api/v1/goals/{id}/milestones", aliceGoal), alice(),
                Map.of("title", "Run 10 km", "deadline", now.plus(Duration.ofDays(10)))));
        String pact = call(HttpStatus.CREATED, post("/api/v1/pacts"), alice(),
                Map.of("goalId", aliceGoal, "charityId", charityId, "depositAmount", 500));
        String pactId = id(pact);
        String alicePid = participantOf(pact, aliceId);
        assertThat(field(call(HttpStatus.OK, get("/api/v1/goals/{id}", aliceGoal), alice(), null), "$.status"))
                .isEqualTo("IN_PACT");

        // --- Bob finds the pact in the feed and joins with his own goal
        String bobGoal = id(call(HttpStatus.CREATED, post("/api/v1/goals"), bob(), Map.of(
                "categoryId", categoryId, "title", "Read 12 books", "deadline", now.plus(Duration.ofDays(20)))));
        String feed = call(HttpStatus.OK, get("/api/v1/goals/feed"), bob(), null);
        assertThat(JsonPath.<List<String>>read(feed, "$.content[*].pactId")).contains(pactId);
        String joined = call(HttpStatus.OK, post("/api/v1/pacts/{id}/join", pactId), bob(),
                Map.of("goalId", bobGoal, "depositAmount", 300));
        String bobPid = participantOf(joined, bobId);

        // --- the pact cannot start before deposits are held
        call(HttpStatus.CONFLICT, post("/api/v1/pacts/{id}/start", pactId), alice(), null);

        // --- deposits: payment page, then payment-service confirms (a repeated event changes nothing)
        assertThat(field(call(HttpStatus.OK, post("/api/v1/pacts/{id}/deposit", pactId), alice(), null), "$.paymentUrl"))
                .isEqualTo("https://pay.test/deposit");
        call(HttpStatus.OK, post("/api/v1/pacts/{id}/deposit", pactId), bob(), null);
        for (String participant : List.of(alicePid, bobPid)) {
            String held = callInternal(HttpStatus.OK, post("/internal/payments/events"),
                    Map.of("participantId", participant, "event", "HELD"));
            assertThat(field(held, "$.participantStatus")).isEqualTo("ACTIVE");
        }
        callInternal(HttpStatus.OK, post("/internal/payments/events"), Map.of("participantId", alicePid, "event", "HELD"));

        // --- only the creator starts it
        call(HttpStatus.FORBIDDEN, post("/api/v1/pacts/{id}/start", pactId), bob(), null);
        assertThat(field(call(HttpStatus.OK, post("/api/v1/pacts/{id}/start", pactId), alice(), null), "$.status"))
                .isEqualTo("ACTIVE");

        // --- milestone report with a link as evidence; self-review is forbidden, the partner approves
        String milestoneReport = id(call(HttpStatus.CREATED, post("/api/v1/pacts/{id}/check-ins", pactId), alice(),
                Map.of("milestoneId", milestoneId, "comment", "Ran 10.2 km")));
        call(HttpStatus.CREATED, post("/api/v1/check-ins/{id}/proofs", milestoneReport), alice(),
                Map.of("externalUrl", "https://strava.example/run/1"));
        call(HttpStatus.FORBIDDEN, post("/api/v1/check-ins/{id}/reviews", milestoneReport), alice(),
                Map.of("status", "APPROVED"));
        String toReview = call(HttpStatus.OK, get("/api/v1/check-ins/to-review"), bob(), null);
        assertThat(JsonPath.<List<String>>read(toReview, "$.content[*].id")).contains(milestoneReport);
        call(HttpStatus.CREATED, post("/api/v1/check-ins/{id}/reviews", milestoneReport), bob(), Map.of("status", "APPROVED"));

        // --- final report, approved: goal completed, deposit released, coins credited
        String finalReport = id(call(HttpStatus.CREATED, post("/api/v1/pacts/{id}/check-ins", pactId), alice(),
                Map.of("comment", "Finished the marathon")));
        call(HttpStatus.CREATED, post("/api/v1/check-ins/{id}/reviews", finalReport), bob(), Map.of("status", "APPROVED"));

        assertThat(field(call(HttpStatus.OK, get("/api/v1/goals/{id}", aliceGoal), alice(), null), "$.status"))
                .isEqualTo("COMPLETED");
        assertThat(JsonPath.<Integer>read(call(HttpStatus.OK, get("/api/v1/users/me"), alice(), null), "$.coins"))
                .isEqualTo(GOAL_COINS);
        String coins = call(HttpStatus.OK, get("/api/v1/users/me/coin-transactions"), alice(), null);
        assertThat(field(coins, "$.content[0].reason")).isEqualTo("GOAL_COMPLETED");
        verify(paymentClient).release(UUID.fromString(alicePid));
        verify(emailClient).sendPactResult(eq("alice-" + run + "@example.com"), eq("Run a marathon"),
                eq(ParticipantStatus.COMPLETED));

        // --- Bob misses his deadline: failed, deposit charged to the charity, the pact is finished
        deadlineService.decide(UUID.fromString(bobPid), now.plus(Duration.ofDays(21)));
        deadlineService.decide(UUID.fromString(bobPid), now.plus(Duration.ofDays(22)));
        verify(paymentClient, times(1)).charge(UUID.fromString(bobPid), UUID.fromString(charityId));
        verify(paymentClient, never()).release(UUID.fromString(bobPid));
        String finished = call(HttpStatus.OK, get("/api/v1/pacts/{id}", pactId), bob(), null);
        assertThat(field(finished, "$.status")).isEqualTo("FINISHED");
        assertThat(JsonPath.<List<String>>read(finished, "$.participants[?(@.id == '" + bobPid + "')].status"))
                .containsExactly("FAILED");
        // Alice's payout is not repeated by later runs
        deadlineService.decide(UUID.fromString(alicePid), now.plus(Duration.ofDays(31)));
        assertThat(JsonPath.<Integer>read(call(HttpStatus.OK, get("/api/v1/users/me"), alice(), null), "$.coins"))
                .isEqualTo(GOAL_COINS);

        // --- the reward is spent: buy an avatar, wear it; not enough coins for a second item
        String avatarId = id(call(HttpStatus.CREATED, post("/api/v1/admin/shop-items"), admin(),
                Map.of("name", "Cat " + run, "price", 60, "itemType", "AVATAR")));
        String frameId = id(call(HttpStatus.CREATED, post("/api/v1/admin/shop-items"), admin(),
                Map.of("name", "Gold frame " + run, "price", 60, "itemType", "FRAME")));

        String purchase = call(HttpStatus.CREATED, post("/api/v1/shop/items/{id}/purchase", avatarId), alice(), null);
        assertThat(JsonPath.<Integer>read(purchase, "$.coinsLeft")).isEqualTo(GOAL_COINS - 60);
        call(HttpStatus.CONFLICT, post("/api/v1/shop/items/{id}/purchase", avatarId), alice(), null);
        call(HttpStatus.CONFLICT, post("/api/v1/shop/items/{id}/purchase", frameId), alice(), null);
        String profile = call(HttpStatus.OK, put("/api/v1/users/me/avatar"), alice(), Map.of("shopItemId", avatarId));
        assertThat(field(profile, "$.activeAvatar.id")).isEqualTo(avatarId);
        assertThat(JsonPath.<Integer>read(profile, "$.coins")).isEqualTo(GOAL_COINS - 60);
    }

    private void register(UUID userId, String name) throws Exception {
        callInternal(HttpStatus.CREATED, post("/internal/users"),
                Map.of("id", userId, "email", name + "-" + run + "@example.com", "username", name));
    }

    private String call(HttpStatus expected, MockHttpServletRequestBuilder request, String token, Object body)
            throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
        return send(expected, request, body);
    }

    private String callInternal(HttpStatus expected, MockHttpServletRequestBuilder request, Object body)
            throws Exception {
        request.header(InternalTokenFilter.HEADER, TestTokens.INTERNAL_TOKEN);
        return send(expected, request, body);
    }

    private String send(HttpStatus expected, MockHttpServletRequestBuilder request, Object body) throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(toJson(body));
        }
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus())
                .as("%s %s -> %s", request.buildRequest(null).getMethod(),
                        request.buildRequest(null).getRequestURI(), response.getContentAsString())
                .isEqualTo(expected.value());
        return response.getContentAsString();
    }

    private String participantOf(String pactJson, UUID userId) {
        List<String> ids = JsonPath.read(pactJson, "$.participants[?(@.user.id == '" + userId + "')].id");
        assertThat(ids).hasSize(1);
        return ids.get(0);
    }

    private static String id(String json) {
        return field(json, "$.id");
    }

    private static String field(String json, String path) {
        return JsonPath.read(json, path);
    }

    // Flat maps only: strings, numbers, UUIDs and Instants as JSON strings
    private static String toJson(Object body) {
        StringBuilder json = new StringBuilder("{");
        ((Map<?, ?>) body).forEach((key, value) -> {
            if (json.length() > 1) {
                json.append(',');
            }
            json.append('"').append(key).append("\":");
            json.append(value instanceof Number ? value.toString() : "\"" + value + "\"");
        });
        return json.append('}').toString();
    }

    private String admin() {
        return TestTokens.admin(adminId);
    }

    private String alice() {
        return TestTokens.user(aliceId);
    }

    private String bob() {
        return TestTokens.user(bobId);
    }
}
