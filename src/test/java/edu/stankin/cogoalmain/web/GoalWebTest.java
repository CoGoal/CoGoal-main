package edu.stankin.cogoalmain.web;

import edu.stankin.cogoalmain.db.entity.enums.GoalStatus;
import edu.stankin.cogoalmain.service.GoalService;
import edu.stankin.cogoalmain.support.TestTokens;
import edu.stankin.cogoalmain.support.WebLayerTest;
import edu.stankin.cogoalmain.web.controllers.GoalController;
import edu.stankin.cogoalmain.web.controllers.MilestoneController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebLayerTest({GoalController.class, MilestoneController.class})
class GoalWebTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GoalService goalService;

    @Test
    void pastDeadlineIsRejected() throws Exception {
        String body = """
                {"categoryId":"%s","title":"Run","deadline":"%s"}"""
                .formatted(UUID.randomUUID(), Instant.now().minus(Duration.ofDays(1)));

        mvc.perform(post("/api/v1/goals").header(HttpHeaders.AUTHORIZATION, user())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("deadline"));

        verifyNoInteractions(goalService);
    }

    @Test
    void milestoneRequiresDeadline() throws Exception {
        mvc.perform(post("/api/v1/goals/{id}/milestones", UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, user())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"First step\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("deadline"));
    }

    @Test
    void myGoalsFilterByStatus() throws Exception {
        given(goalService.getMyGoals(eq(USER_ID), eq(GoalStatus.IN_PACT), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/goals/my").param("status", "IN_PACT").header(HttpHeaders.AUTHORIZATION, user()))
                .andExpect(status().isOk());

        verify(goalService).getMyGoals(eq(USER_ID), eq(GoalStatus.IN_PACT), any(Pageable.class));
    }

    @Test
    void unknownStatusIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/goals/my").param("status", "WHATEVER").header(HttpHeaders.AUTHORIZATION, user()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void feedIsNotMistakenForGoalId() throws Exception {
        given(goalService.feed(eq(USER_ID), isNull(), any(Pageable.class))).willReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/goals/feed").header(HttpHeaders.AUTHORIZATION, user()))
                .andExpect(status().isOk());

        verify(goalService).feed(eq(USER_ID), isNull(), any(Pageable.class));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        UUID goalId = UUID.randomUUID();

        mvc.perform(delete("/api/v1/goals/{id}", goalId).header(HttpHeaders.AUTHORIZATION, user()))
                .andExpect(status().isNoContent());

        verify(goalService).delete(USER_ID, goalId);
    }

    private static String user() {
        return TestTokens.bearer(TestTokens.user(USER_ID));
    }
}
