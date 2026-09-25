package edu.stankin.cogoalmain.service;

import edu.stankin.cogoalmain.db.entity.CheckIn;
import edu.stankin.cogoalmain.db.entity.Pact;
import edu.stankin.cogoalmain.db.entity.Proof;
import edu.stankin.cogoalmain.db.entity.User;
import edu.stankin.cogoalmain.db.entity.enums.CheckInStatus;
import edu.stankin.cogoalmain.db.entity.enums.DepositStatus;
import edu.stankin.cogoalmain.db.entity.enums.PactStatus;
import edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus;
import edu.stankin.cogoalmain.db.entity.enums.ProofType;
import edu.stankin.cogoalmain.db.repo.CheckInRepository;
import edu.stankin.cogoalmain.db.repo.ProofRepository;
import edu.stankin.cogoalmain.exception.BusinessRuleException;
import edu.stankin.cogoalmain.exception.ForbiddenException;
import edu.stankin.cogoalmain.service.event.ProofFileDeleted;
import edu.stankin.cogoalmain.storage.FileStorage;
import edu.stankin.cogoalmain.web.dto.checkin.LinkProofRequest;
import edu.stankin.cogoalmain.web.dto.checkin.ProofResponse;
import edu.stankin.cogoalmain.web.mapper.CheckInMapperImpl;
import edu.stankin.cogoalmain.web.mapper.GoalMapperImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static edu.stankin.cogoalmain.support.PactFixtures.pact;
import static edu.stankin.cogoalmain.support.PactFixtures.participant;
import static edu.stankin.cogoalmain.support.PactFixtures.user;
import static edu.stankin.cogoalmain.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProofServiceTest {

    private static final String KEY = "3f1c2a4e-9b7d-4c1e-8a2f-5d6e7f8a9b0c.jpg";

    @Mock
    private PactService pactService;
    @Mock
    private CheckInService checkInService;
    @Mock
    private CheckInRepository checkInRepository;
    @Mock
    private ProofRepository proofRepository;
    @Mock
    private FileStorage fileStorage;
    @Mock
    private ApplicationEventPublisher events;

    private ProofService service;

    private final User alice = user("alice");
    private final User bob = user("bob");
    private Pact pact;
    private CheckIn checkIn;

    @BeforeEach
    void setUp() throws IOException {
        service = new ProofService(pactService, checkInService, checkInRepository, proofRepository, fileStorage,
                new CheckInMapperImpl(new GoalMapperImpl()), events);

        pact = pact(alice, PactStatus.ACTIVE);
        checkIn = withId(new CheckIn());
        checkIn.setParticipant(participant(pact, alice, ParticipantStatus.ACTIVE, DepositStatus.HELD));
        checkIn.setStatus(CheckInStatus.PENDING);

        given(checkInRepository.findPactIdById(checkIn.getId())).willReturn(Optional.of(pact.getId()));
        given(checkInService.findDetailed(checkIn.getId())).willReturn(checkIn);
        given(fileStorage.store(any(), any())).willReturn(KEY);
        given(proofRepository.save(any())).willAnswer(invocation -> withId(invocation.<Proof>getArgument(0)));
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void authorAttachesPhotoAndGetsDownloadPathInsteadOfStorageKey() {
        ProofResponse response = addPhoto(alice);

        assertThat(response.type()).isEqualTo(ProofType.PHOTO);
        assertThat(response.downloadUrl()).isEqualTo("/api/v1/proofs/" + response.id() + "/file");
        assertThat(response.toString()).doesNotContain(KEY);
    }

    @Test
    void storedFileIsDeletedWhenTheTransactionRollsBack() throws IOException {
        TransactionSynchronizationManager.initSynchronization();

        addPhoto(alice);
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        verify(fileStorage).delete(KEY);
    }

    @Test
    void storedFileIsKeptWhenTheTransactionCommits() throws IOException {
        TransactionSynchronizationManager.initSynchronization();

        addPhoto(alice);
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        }

        verify(fileStorage, never()).delete(any());
    }

    @Test
    void onlyTheAuthorAddsProofs() {
        assertThatThrownBy(() -> addPhoto(bob)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(fileStorage);
    }

    @Test
    void decidedReportCannotGetNewProofs() {
        checkIn.setStatus(CheckInStatus.APPROVED);

        assertThatThrownBy(() -> service.addLink(alice.getId(), checkIn.getId(),
                new LinkProofRequest("https://strava.com/run/1", null)))
                .isInstanceOf(BusinessRuleException.class);
        verify(proofRepository, never()).save(any());
    }

    @Test
    void deletingFileProofRemovesFileAfterCommit() {
        Proof proof = withId(new Proof());
        proof.setCheckIn(checkIn);
        proof.setType(ProofType.FILE);
        proof.setFileUrl(KEY);
        given(proofRepository.findPactIdById(proof.getId())).willReturn(Optional.of(pact.getId()));
        given(proofRepository.findWithCheckInById(proof.getId())).willReturn(Optional.of(proof));

        service.delete(alice.getId(), proof.getId());

        verify(proofRepository).delete(proof);
        verify(events).publishEvent(new ProofFileDeleted(KEY));
        verifyNoInteractions(fileStorage);
    }

    private ProofResponse addPhoto(User user) {
        InputStream content = new ByteArrayInputStream(new byte[]{1, 2, 3});
        return service.addFile(user.getId(), checkIn.getId(), ProofType.PHOTO, content, "run.jpg", "Finish line");
    }
}
