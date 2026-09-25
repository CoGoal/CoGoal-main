package edu.stankin.cogoalmain.service;

import edu.stankin.cogoalmain.db.entity.CheckIn;
import edu.stankin.cogoalmain.db.entity.Proof;
import edu.stankin.cogoalmain.db.entity.enums.CheckInStatus;
import edu.stankin.cogoalmain.db.entity.enums.ProofType;
import edu.stankin.cogoalmain.db.repo.CheckInRepository;
import edu.stankin.cogoalmain.db.repo.ProofRepository;
import edu.stankin.cogoalmain.exception.BusinessRuleException;
import edu.stankin.cogoalmain.exception.ForbiddenException;
import edu.stankin.cogoalmain.exception.NotFoundException;
import edu.stankin.cogoalmain.service.event.ProofFileDeleted;
import edu.stankin.cogoalmain.storage.FileStorage;
import edu.stankin.cogoalmain.web.dto.checkin.LinkProofRequest;
import edu.stankin.cogoalmain.web.dto.checkin.ProofResponse;
import edu.stankin.cogoalmain.web.mapper.CheckInMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.UUID;

/**
 * Evidence attached to reports. Only the author adds or removes it, and only while the report is pending.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProofService {

    private final PactService pactService;
    private final CheckInService checkInService;
    private final CheckInRepository checkInRepository;
    private final ProofRepository proofRepository;
    private final FileStorage fileStorage;
    private final CheckInMapper checkInMapper;
    private final ApplicationEventPublisher events;

    /**
     * Stores an uploaded file as a FILE or PHOTO proof. Size and content type are checked by the caller.
     */
    @Transactional
    public ProofResponse addFile(UUID userId, UUID checkInId, ProofType type, InputStream content,
                                 String originalFilename, String description) {
        if (type == ProofType.LINK) {
            throw new IllegalArgumentException("LINK proofs have no file");
        }
        CheckIn checkIn = lockOwnPending(userId, checkInId);

        String key = store(content, originalFilename);
        deleteFileIfRolledBack(key);

        Proof proof = newProof(checkIn, type, description);
        proof.setFileUrl(key);
        return checkInMapper.toResponse(proofRepository.save(proof));
    }

    @Transactional
    public ProofResponse addLink(UUID userId, UUID checkInId, LinkProofRequest request) {
        CheckIn checkIn = lockOwnPending(userId, checkInId);

        Proof proof = newProof(checkIn, ProofType.LINK, request.description());
        proof.setExternalUrl(request.externalUrl());
        return checkInMapper.toResponse(proofRepository.save(proof));
    }

    @Transactional
    public void delete(UUID userId, UUID proofId) {
        UUID pactId = proofRepository.findPactIdById(proofId).orElseThrow(() -> NotFoundException.of("Proof", proofId));
        pactService.lock(pactId);
        Proof proof = proofRepository.findWithCheckInById(proofId)
                .orElseThrow(() -> NotFoundException.of("Proof", proofId));
        requireOwnPending(proof.getCheckIn(), userId);

        proofRepository.delete(proof);
        if (proof.getFileUrl() != null) {
            events.publishEvent(new ProofFileDeleted(proof.getFileUrl()));
        }
    }

    /** The uploaded file of a proof, for participants of the pact (e.g. reviewers). */
    @Transactional(readOnly = true)
    public ProofFile download(UUID userId, UUID proofId) {
        UUID pactId = proofRepository.findPactIdById(proofId).orElseThrow(() -> NotFoundException.of("Proof", proofId));
        pactService.requireMember(pactId, userId);

        Proof proof = proofRepository.findById(proofId).orElseThrow(() -> NotFoundException.of("Proof", proofId));
        if (proof.getFileUrl() == null) {
            throw new NotFoundException("Proof " + proofId + " is a link and has no file");
        }
        Resource file = fileStorage.load(proof.getFileUrl())
                .orElseThrow(() -> new NotFoundException("File of proof " + proofId + " is missing"));
        MediaType mediaType = MediaTypeFactory.getMediaType(proof.getFileUrl())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return new ProofFile(file, proof.getFileUrl(), mediaType);
    }

    private CheckIn lockOwnPending(UUID userId, UUID checkInId) {
        UUID pactId = checkInRepository.findPactIdById(checkInId)
                .orElseThrow(() -> NotFoundException.of("Check-in", checkInId));
        pactService.lock(pactId);
        CheckIn checkIn = checkInService.findDetailed(checkInId);
        requireOwnPending(checkIn, userId);
        return checkIn;
    }

    private static void requireOwnPending(CheckIn checkIn, UUID userId) {
        if (!checkIn.getParticipant().getUser().getId().equals(userId)) {
            throw new ForbiddenException("Only the author of the report can change its proofs");
        }
        if (checkIn.getStatus() != CheckInStatus.PENDING) {
            throw new BusinessRuleException("Proofs can only be changed while the report is pending");
        }
    }

    private static Proof newProof(CheckIn checkIn, ProofType type, String description) {
        Proof proof = new Proof();
        proof.setCheckIn(checkIn);
        proof.setType(type);
        proof.setDescription(description);
        return proof;
    }

    private String store(InputStream content, String originalFilename) {
        try {
            return fileStorage.store(content, originalFilename);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store the uploaded file", e);
        }
    }

    // The file is written before the proof row is committed; do not leave it behind if the commit fails
    private void deleteFileIfRolledBack(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        fileStorage.delete(key);
                    } catch (IOException e) {
                        log.warn("Could not delete file {} of a rolled back proof", key, e);
                    }
                }
            }
        });
    }

    /**
     * @param filename name to offer when downloading (the generated storage name, never the client's)
     */
    public record ProofFile(Resource resource, String filename, MediaType mediaType) {
    }
}
