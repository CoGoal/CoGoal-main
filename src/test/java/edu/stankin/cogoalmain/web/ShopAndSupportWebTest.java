package edu.stankin.cogoalmain.web;

import edu.stankin.cogoalmain.db.entity.enums.ShopItemType;
import edu.stankin.cogoalmain.db.entity.enums.SupportTicketStatus;
import edu.stankin.cogoalmain.exception.BusinessRuleException;
import edu.stankin.cogoalmain.service.ShopService;
import edu.stankin.cogoalmain.service.SupportService;
import edu.stankin.cogoalmain.support.TestTokens;
import edu.stankin.cogoalmain.support.WebLayerTest;
import edu.stankin.cogoalmain.web.controllers.ShopController;
import edu.stankin.cogoalmain.web.controllers.SupportController;
import edu.stankin.cogoalmain.web.controllers.admin.SupportTicketAdminController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebLayerTest({ShopController.class, SupportController.class, SupportTicketAdminController.class})
class ShopAndSupportWebTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ShopService shopService;
    @MockitoBean
    private SupportService supportService;

    @Test
    void notEnoughCoinsIsConflict() throws Exception {
        UUID itemId = UUID.randomUUID();
        given(shopService.purchase(USER_ID, itemId)).willThrow(new BusinessRuleException("Not enough coins: the item costs 150"));

        mvc.perform(post("/api/v1/shop/items/{id}/purchase", itemId).header(HttpHeaders.AUTHORIZATION, user()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Not enough coins: the item costs 150"));
    }

    @Test
    void shopFiltersByType() throws Exception {
        given(shopService.listActive(eq(ShopItemType.FRAME), any(Pageable.class))).willReturn(Page.empty());

        mvc.perform(get("/api/v1/shop/items").param("type", "FRAME").header(HttpHeaders.AUTHORIZATION, user()))
                .andExpect(status().isOk());

        verify(shopService).listActive(eq(ShopItemType.FRAME), any(Pageable.class));
    }

    @Test
    void ticketNeedsSubject() throws Exception {
        mvc.perform(post("/api/v1/support/tickets").header(HttpHeaders.AUTHORIZATION, user())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"help\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("subject"));
        verifyNoInteractions(supportService);
    }

    @Test
    void adminTicketEndpointsAreForbiddenForUsers() throws Exception {
        mvc.perform(get("/api/v1/admin/support-tickets").header(HttpHeaders.AUTHORIZATION, user()))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/admin/support-tickets/{id}", UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, user())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(supportService);
    }

    @Test
    void adminFiltersTicketsByStatus() throws Exception {
        given(supportService.listAll(eq(SupportTicketStatus.OPEN), any(Pageable.class))).willReturn(Page.empty());

        mvc.perform(get("/api/v1/admin/support-tickets").param("status", "OPEN")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.admin(USER_ID))))
                .andExpect(status().isOk());

        verify(supportService).listAll(eq(SupportTicketStatus.OPEN), any(Pageable.class));
    }

    private static String user() {
        return TestTokens.bearer(TestTokens.user(USER_ID));
    }
}
