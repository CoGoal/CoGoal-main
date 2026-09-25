package edu.stankin.cogoalmain.web;

import edu.stankin.cogoalmain.config.security.CurrentUserId;
import edu.stankin.cogoalmain.config.security.InternalTokenFilter;
import edu.stankin.cogoalmain.exception.BusinessRuleException;
import edu.stankin.cogoalmain.exception.ForbiddenException;
import edu.stankin.cogoalmain.exception.NotFoundException;
import edu.stankin.cogoalmain.service.UserService;
import edu.stankin.cogoalmain.support.TestTokens;
import edu.stankin.cogoalmain.support.WebLayerTest;
import edu.stankin.cogoalmain.web.controllers.internal.InternalUserController;
import edu.stankin.cogoalmain.web.dto.internal.InternalUserResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security chains, {@code @CurrentUserId} and the ProblemDetail error format, through real HTTP requests
 * with real signed tokens. Business logic is mocked.
 */
@WebLayerTest({InternalUserController.class, SecurityAndErrorHandlingTest.ProbeController.class})
class SecurityAndErrorHandlingTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserService userService;

    @Nested
    class UserChain {

        @Test
        void rejectsRequestWithoutToken() throws Exception {
            mvc.perform(get("/api/v1/probe/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.detail").value("Bearer token is required"));
        }

        @Test
        void rejectsInvalidToken() throws Exception {
            mvc.perform(get("/api/v1/probe/me").header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Invalid or expired bearer token"));
        }

        @Test
        void resolvesCurrentUserIdFromToken() throws Exception {
            mvc.perform(get("/api/v1/probe/me").header(HttpHeaders.AUTHORIZATION, userBearer()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(USER_ID.toString()));
        }

        @Test
        void adminPathsRequireAdminRole() throws Exception {
            mvc.perform(get("/api/v1/admin/probe").header(HttpHeaders.AUTHORIZATION, userBearer()))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

            mvc.perform(get("/api/v1/admin/probe")
                            .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.admin(USER_ID))))
                    .andExpect(status().isOk());
        }

        @Test
        void internalTokenIsNotAcceptedOnUserEndpoints() throws Exception {
            mvc.perform(get("/api/v1/probe/me").header(InternalTokenFilter.HEADER, TestTokens.INTERNAL_TOKEN))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class InternalChain {

        private static final String BODY = """
                {"id":"%s","email":"new@example.com","username":"newbie"}""".formatted(USER_ID);

        @Test
        void rejectsMissingOrWrongInternalToken() throws Exception {
            mvc.perform(post("/internal/users").contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Internal token is required"));

            mvc.perform(post("/internal/users").contentType(MediaType.APPLICATION_JSON).content(BODY)
                            .header(InternalTokenFilter.HEADER, "wrong-token-wrong-token"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Invalid internal token"));
        }

        @Test
        void userBearerTokenIsNotAcceptedOnInternalEndpoints() throws Exception {
            mvc.perform(post("/internal/users").contentType(MediaType.APPLICATION_JSON).content(BODY)
                            .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.admin(USER_ID))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void createsProfileWith201AndReturns200WhenItAlreadyExists() throws Exception {
            InternalUserResponse user = new InternalUserResponse(USER_ID, "new@example.com", "newbie");

            given(userService.register(any())).willReturn(new UserService.Registration(user, true));
            mvc.perform(post("/internal/users").contentType(MediaType.APPLICATION_JSON).content(BODY)
                            .header(InternalTokenFilter.HEADER, TestTokens.INTERNAL_TOKEN))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                    .andExpect(jsonPath("$.username").value("newbie"));

            given(userService.register(any())).willReturn(new UserService.Registration(user, false));
            mvc.perform(post("/internal/users").contentType(MediaType.APPLICATION_JSON).content(BODY)
                            .header(InternalTokenFilter.HEADER, TestTokens.INTERNAL_TOKEN))
                    .andExpect(status().isOk());
        }

        @Test
        void validatesBody() throws Exception {
            mvc.perform(post("/internal/users").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"id":"%s","email":"not-an-email","username":""}""".formatted(USER_ID))
                            .header(InternalTokenFilter.HEADER, TestTokens.INTERNAL_TOKEN))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.length()").value(2));
        }
    }

    @Nested
    class ErrorFormat {

        @Test
        void mapsDomainExceptionsToProblemDetail() throws Exception {
            expectProblem("/api/v1/probe/not-found", 404, "Goal 42 not found");
            expectProblem("/api/v1/probe/forbidden", 403, "Not your goal");
            expectProblem("/api/v1/probe/conflict", 409, "Pact is not open");
        }

        @Test
        void validationErrorListsFailingFields() throws Exception {
            mvc.perform(post("/api/v1/probe/validate")
                            .header(HttpHeaders.AUTHORIZATION, userBearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\" \"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.errors[0].field").value("name"));
        }

        @Test
        void malformedJsonIsBadRequest() throws Exception {
            mvc.perform(post("/api/v1/probe/validate")
                            .header(HttpHeaders.AUTHORIZATION, userBearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{not json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        private void expectProblem(String path, int status, String detail) throws Exception {
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, userBearer()))
                    .andExpect(status().is(status))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(status))
                    .andExpect(jsonPath("$.detail").value(detail));
        }
    }

    private static String userBearer() {
        return TestTokens.bearer(TestTokens.user(USER_ID));
    }

    @RestController
    static class ProbeController {

        @GetMapping("/api/v1/probe/me")
        String me(@CurrentUserId UUID userId) {
            return userId.toString();
        }

        @GetMapping("/api/v1/admin/probe")
        String admin() {
            return "ok";
        }

        @GetMapping("/api/v1/probe/not-found")
        void notFound() {
            throw NotFoundException.of("Goal", 42);
        }

        @GetMapping("/api/v1/probe/forbidden")
        void forbidden() {
            throw new ForbiddenException("Not your goal");
        }

        @GetMapping("/api/v1/probe/conflict")
        void conflict() {
            throw new BusinessRuleException("Pact is not open");
        }

        @PostMapping("/api/v1/probe/validate")
        void validate(@Valid @RequestBody NamedRequest request) {
        }

        record NamedRequest(@NotBlank String name) {
        }
    }
}
