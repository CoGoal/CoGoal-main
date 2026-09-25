package edu.stankin.cogoalmain.web.controllers;

import edu.stankin.cogoalmain.config.AppProperties;
import edu.stankin.cogoalmain.config.security.CurrentUserId;
import edu.stankin.cogoalmain.service.ProofService;
import edu.stankin.cogoalmain.web.dto.checkin.FileProofType;
import edu.stankin.cogoalmain.web.dto.checkin.LinkProofRequest;
import edu.stankin.cogoalmain.web.dto.checkin.ProofResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Reports")
public class ProofController {

    private final ProofService proofService;
    private final AppProperties properties;

    @PostMapping(path = "/check-ins/{id}/proofs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Attach a file or photo to the own pending report",
            description = "Multipart: 'file' plus 'type' (FILE or PHOTO; a PHOTO must be an image). "
                    + "Size is limited by app.storage.max-file-size")
    public ProofResponse addFile(@CurrentUserId UUID userId, @PathVariable UUID id,
                                 @RequestPart("file") MultipartFile file,
                                 @RequestParam FileProofType type,
                                 @RequestParam(required = false) @Size(max = 2000) String description) throws IOException {
        requireAcceptable(file, type);
        return proofService.addFile(userId, id, type.proofType(), file.getInputStream(),
                file.getOriginalFilename(), description);
    }

    @PostMapping(path = "/check-ins/{id}/proofs", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Attach a link (LINK proof) to the own pending report")
    public ProofResponse addLink(@CurrentUserId UUID userId, @PathVariable UUID id,
                                 @Valid @RequestBody LinkProofRequest request) {
        return proofService.addLink(userId, id, request);
    }

    @DeleteMapping("/proofs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a proof from the own pending report")
    public void delete(@CurrentUserId UUID userId, @PathVariable UUID id) {
        proofService.delete(userId, id);
    }

    @GetMapping("/proofs/{id}/file")
    @Operation(summary = "Download the file of a proof (participants of the pact only)")
    public ResponseEntity<Resource> download(@CurrentUserId UUID userId, @PathVariable UUID id) {
        ProofService.ProofFile file = proofService.download(userId, id);
        // Always as an attachment: an uploaded HTML or SVG file must never run in the API's origin
        return ResponseEntity.ok()
                .contentType(file.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename()).build().toString())
                .body(file.resource());
    }

    private void requireAcceptable(MultipartFile file, FileProofType type) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file is empty");
        }
        long maxBytes = properties.storage().maxFileSize().toBytes();
        if (file.getSize() > maxBytes) {
            throw new MaxUploadSizeExceededException(maxBytes);
        }
        String contentType = file.getContentType();
        if (type == FileProofType.PHOTO && (contentType == null || !contentType.startsWith("image/"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A PHOTO proof must be an image");
        }
    }
}
