package edu.stankin.cogoalmain.web;

import edu.stankin.cogoalmain.service.CategoryService;
import edu.stankin.cogoalmain.service.CharityService;
import edu.stankin.cogoalmain.service.ShopItemService;
import edu.stankin.cogoalmain.service.UserService;
import edu.stankin.cogoalmain.support.TestTokens;
import edu.stankin.cogoalmain.support.WebLayerTest;
import edu.stankin.cogoalmain.web.controllers.CatalogController;
import edu.stankin.cogoalmain.web.controllers.UserController;
import edu.stankin.cogoalmain.web.controllers.admin.CategoryAdminController;
import edu.stankin.cogoalmain.web.controllers.admin.CharityAdminController;
import edu.stankin.cogoalmain.web.controllers.admin.ShopItemAdminController;
import edu.stankin.cogoalmain.web.dto.catalog.CategoryResponse;
import edu.stankin.cogoalmain.web.dto.user.MyProfileResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebLayerTest({
        UserController.class, CatalogController.class,
        CategoryAdminController.class, CharityAdminController.class, ShopItemAdminController.class
})
class ProfileAndCatalogWebTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserService userService;
    @MockitoBean
    private CategoryService categoryService;
    @MockitoBean
    private CharityService charityService;
    @MockitoBean
    private ShopItemService shopItemService;

    @Test
    void myProfileUsesIdFromToken() throws Exception {
        given(userService.getMyProfile(USER_ID)).willReturn(new MyProfileResponse(
                USER_ID, "alice", "a@example.com", null, null, null, null, 10, Instant.now()));

        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.coins").value(10));
    }

    @Test
    void blankUsernameIsRejected() throws Exception {
        mvc.perform(patch("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("username"));

        verifyNoInteractions(userService);
    }

    @Test
    void searchRequiresQuery() throws Exception {
        mvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, user()))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/users").param("search", " ").header(HttpHeaders.AUTHORIZATION, user()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pagesHaveStableJsonShape() throws Exception {
        CategoryResponse sport = new CategoryResponse(UUID.randomUUID(), "Sport", null, true);
        given(categoryService.listActive(any())).willReturn(new PageImpl<>(List.of(sport), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/categories").header(HttpHeaders.AUTHORIZATION, user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Sport"))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.size").value(20));
    }

    @Test
    void defaultPageSizeComesFromConfiguration() throws Exception {
        given(categoryService.listActive(any())).willReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/categories").header(HttpHeaders.AUTHORIZATION, user()))
                .andExpect(status().isOk());

        // spring.data.web.pageable.default-page-size, not the built-in 10 of @PageableDefault
        verify(categoryService).listActive(eq(PageRequest.of(0, 20, Sort.by("name"))));
    }

    @Test
    void pageSizeIsCapped() throws Exception {
        given(categoryService.listActive(any())).willReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/categories").param("size", "5000").header(HttpHeaders.AUTHORIZATION, user()))
                .andExpect(status().isOk());

        verify(categoryService).listActive(eq(PageRequest.of(0, 100, Sort.by("name"))));
    }

    @Test
    void adminEndpointsAreForbiddenForUsers() throws Exception {
        mvc.perform(post("/api/v1/admin/categories").header(HttpHeaders.AUTHORIZATION, user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sport\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(categoryService);
    }

    @Test
    void adminCreatesCategoryWithLocation() throws Exception {
        UUID id = UUID.randomUUID();
        given(categoryService.create(any())).willReturn(new CategoryResponse(id, "Sport", null, true));

        mvc.perform(post("/api/v1/admin/categories").header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sport\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost/api/v1/admin/categories/" + id))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void shopItemPriceMustBePositive() throws Exception {
        mvc.perform(post("/api/v1/admin/shop-items").header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cat\",\"price\":0,\"itemType\":\"AVATAR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("price"));
    }

    @Test
    void charityWebsiteMustBeUrl() throws Exception {
        mvc.perform(post("/api/v1/admin/charities").header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Fund\",\"websiteUrl\":\"not a url\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("websiteUrl"));
    }

    private static String user() {
        return TestTokens.bearer(TestTokens.user(USER_ID));
    }

    private static String admin() {
        return TestTokens.bearer(TestTokens.admin(USER_ID));
    }
}
