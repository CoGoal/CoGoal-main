package edu.stankin.cogoalmain.web;

import edu.stankin.cogoalmain.config.security.InternalTokenFilter;
import edu.stankin.cogoalmain.db.entity.enums.DepositStatus;
import edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus;
import edu.stankin.cogoalmain.service.DepositService;
import edu.stankin.cogoalmain.service.InvitationService;
import edu.stankin.cogoalmain.service.MessageService;
import edu.stankin.cogoalmain.service.PactService;
import edu.stankin.cogoalmain.support.TestTokens;
import edu.stankin.cogoalmain.support.WebLayerTest;
import edu.stankin.cogoalmain.web.controllers.InvitationController;
import edu.stankin.cogoalmain.web.controllers.MessageController;
import edu.stankin.cogoalmain.web.controllers.PactController;
import edu.stankin.cogoalmain.web.controllers.internal.PaymentEventController;
import edu.stankin.cogoalmain.web.dto.internal.PaymentEventRequest;
import edu.stankin.cogoalmain.web.dto.pact.DepositResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebLayerTest({PactController.class, InvitationController.class, MessageController.class, PaymentEventController.class})
class PactWebTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PactService pactService;
    @MockitoBean
    private DepositService depositService;
    @MockitoBean
    private InvitationService invitationService;
    @MockitoBean
    private MessageService messageService;

    @Test
    void depositAmountMustBePositiveWithAtMostTwoDecimals() throws Exception {
        for (String amount : new String[]{"0", "-5", "10.555"}) {
            mvc.perform(post("/api/v1/pacts/{id}/join", UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, user())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"goalId\":\"%s\",\"depositAmount\":%s}".formatted(UUID.randomUUID(), amount)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("depositAmount"));
        }
        verifyNoInteractions(pactService);
    }

    @Test
    void chatLimitIsBounded() throws Exception {
        mvc.perform(get("/api/v1/pacts/{id}/messages", UUID.randomUUID()).param("limit", "101")
                        .header(HttpHeaders.AUTHORIZATION, user()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(messageService);
    }

    @Test
    void emptyMessageIsRejected() throws Exception {
        mvc.perform(post("/api/v1/pacts/{id}/messages", UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, user())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void paymentEventsRequireInternalToken() throws Exception {
        String body = "{\"participantId\":\"%s\",\"event\":\"HELD\"}".formatted(UUID.randomUUID());

        mvc.perform(post("/internal/payments/events").header(HttpHeaders.AUTHORIZATION, user())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(depositService);
    }

    @Test
    void paymentEventIsAppliedWithInternalToken() throws Exception {
        UUID participantId = UUID.randomUUID();
        given(depositService.handleEvent(any(PaymentEventRequest.class))).willReturn(new DepositResponse(
                participantId, new BigDecimal("500.00"), "RUB", DepositStatus.HELD, ParticipantStatus.ACTIVE));

        mvc.perform(post("/internal/payments/events").header(InternalTokenFilter.HEADER, TestTokens.INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participantId\":\"%s\",\"event\":\"HELD\"}".formatted(participantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depositStatus").value("HELD"))
                .andExpect(jsonPath("$.participantStatus").value("ACTIVE"));
    }

    @Test
    void unknownPaymentEventIsBadRequest() throws Exception {
        mvc.perform(post("/internal/payments/events").header(InternalTokenFilter.HEADER, TestTokens.INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participantId\":\"%s\",\"event\":\"PENDING\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(depositService);
    }

    private static String user() {
        return TestTokens.bearer(TestTokens.user(USER_ID));
    }
}
