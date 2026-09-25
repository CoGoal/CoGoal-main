package edu.stankin.cogoalmain.web;

import edu.stankin.cogoalmain.config.AppProperties;
import edu.stankin.cogoalmain.db.entity.enums.ProofType;
import edu.stankin.cogoalmain.service.CheckInService;
import edu.stankin.cogoalmain.service.ProofService;
import edu.stankin.cogoalmain.service.ReviewService;
import edu.stankin.cogoalmain.support.TestTokens;
import edu.stankin.cogoalmain.support.WebLayerTest;
import edu.stankin.cogoalmain.web.controllers.CheckInController;
import edu.stankin.cogoalmain.web.controllers.ProofController;
import edu.stankin.cogoalmain.web.dto.checkin.ProofResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebLayerTest({CheckInController.class, ProofController.class})
class CheckInWebTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CHECK_IN_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private AppProperties properties;

    @MockitoBean
    private CheckInService checkInService;
    @MockitoBean
    private ReviewService reviewService;
    @MockitoBean
    private ProofService proofService;

    @Test
    void rejectionRequiresComment() throws Exception {
        mvc.perform(post("/api/v1/check-ins/{id}/reviews", CHECK_IN_ID).header(HttpHeaders.AUTHORIZATION, user())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REJECTED\",\"comment\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("comment"));
        verifyNoInteractions(reviewService);
    }

    @Test
    void approvalNeedsNoComment() throws Exception {
        mvc.perform(post("/api/v1/check-ins/{id}/reviews", CHECK_IN_ID).header(HttpHeaders.AUTHORIZATION, user())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void photoMustBeAnImage() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[]{1});

        mvc.perform(multipart("/api/v1/check-ins/{id}/proofs", CHECK_IN_ID).file(pdf).param("type", "PHOTO")
                        .header(HttpHeaders.AUTHORIZATION, user()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A PHOTO proof must be an image"));
        verifyNoInteractions(proofService);
    }

    @Test
    void emptyFileIsRejected() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "a.txt", "text/plain", new byte[0]);

        mvc.perform(multipart("/api/v1/check-ins/{id}/proofs", CHECK_IN_ID).file(empty).param("type", "FILE")
                        .header(HttpHeaders.AUTHORIZATION, user()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fileAboveConfiguredLimitIsRejected() throws Exception {
        int tooBig = (int) properties.storage().maxFileSize().toBytes() + 1;
        MockMultipartFile big = new MockMultipartFile("file", "big.bin", "application/octet-stream", new byte[tooBig]);

        mvc.perform(multipart("/api/v1/check-ins/{id}/proofs", CHECK_IN_ID).file(big).param("type", "FILE")
                        .header(HttpHeaders.AUTHORIZATION, user()))
                .andExpect(status().isPayloadTooLarge());
        verifyNoInteractions(proofService);
    }

    @Test
    void linkTypeCannotBeUploadedAsFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", new byte[]{1});

        mvc.perform(multipart("/api/v1/check-ins/{id}/proofs", CHECK_IN_ID).file(file).param("type", "LINK")
                        .header(HttpHeaders.AUTHORIZATION, user()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validPhotoIsPassedToService() throws Exception {
        MockMultipartFile photo = new MockMultipartFile("file", "run.jpg", "image/jpeg", new byte[]{1, 2});
        given(proofService.addFile(eq(USER_ID), eq(CHECK_IN_ID), eq(ProofType.PHOTO), any(), eq("run.jpg"), any()))
                .willReturn(new ProofResponse(UUID.randomUUID(), ProofType.PHOTO, "/api/v1/proofs/x/file", null,
                        null, Instant.now()));

        mvc.perform(multipart("/api/v1/check-ins/{id}/proofs", CHECK_IN_ID).file(photo).param("type", "PHOTO")
                        .header(HttpHeaders.AUTHORIZATION, user()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("PHOTO"));
    }

    @Test
    void onlyHttpLinksAreAccepted() throws Exception {
        mvc.perform(post("/api/v1/check-ins/{id}/proofs", CHECK_IN_ID).header(HttpHeaders.AUTHORIZATION, user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalUrl\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("externalUrl"));
        verifyNoInteractions(proofService);
    }

    @Test
    void downloadIsAlwaysAnAttachment() throws Exception {
        UUID proofId = UUID.randomUUID();
        given(proofService.download(USER_ID, proofId)).willReturn(new ProofService.ProofFile(
                new ByteArrayResource("<svg/>".getBytes()), "f.svg", MediaType.valueOf("image/svg+xml")));

        mvc.perform(get("/api/v1/proofs/{id}/file", proofId).header(HttpHeaders.AUTHORIZATION, user()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void toReviewIsNotMistakenForCheckInId() throws Exception {
        given(checkInService.toReview(eq(USER_ID), any())).willReturn(org.springframework.data.domain.Page.empty());

        mvc.perform(get("/api/v1/check-ins/to-review").header(HttpHeaders.AUTHORIZATION, user()))
                .andExpect(status().isOk());
    }

    private static String user() {
        return TestTokens.bearer(TestTokens.user(USER_ID));
    }
}
