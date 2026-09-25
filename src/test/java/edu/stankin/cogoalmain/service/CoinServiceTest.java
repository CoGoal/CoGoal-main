package edu.stankin.cogoalmain.service;

import edu.stankin.cogoalmain.db.entity.CoinTransaction;
import edu.stankin.cogoalmain.db.entity.User;
import edu.stankin.cogoalmain.db.entity.enums.CoinReason;
import edu.stankin.cogoalmain.db.repo.CoinTransactionRepository;
import edu.stankin.cogoalmain.db.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CoinServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID GOAL_ID = UUID.randomUUID();

    @Mock
    private CoinTransactionRepository coinTransactionRepository;
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private CoinService service;

    @Test
    void creditBooksTransactionAndIncreasesBalance() {
        given(userRepository.getReferenceById(USER_ID)).willReturn(new User());

        assertThat(service.credit(USER_ID, 100, CoinReason.GOAL_COMPLETED, GOAL_ID)).isTrue();

        verify(coinTransactionRepository).save(org.mockito.ArgumentMatchers.<CoinTransaction>argThat(t ->
                t.getAmount() == 100 && t.getReason() == CoinReason.GOAL_COMPLETED && GOAL_ID.equals(t.getReferenceId())));
        verify(userRepository).creditCoins(USER_ID, 100);
    }

    @Test
    void sameReasonAndReferenceIsCreditedOnlyOnce() {
        given(coinTransactionRepository.existsByReasonAndReferenceId(CoinReason.GOAL_COMPLETED, GOAL_ID)).willReturn(true);

        assertThat(service.credit(USER_ID, 100, CoinReason.GOAL_COMPLETED, GOAL_ID)).isFalse();

        verify(coinTransactionRepository, never()).save(any());
        verify(userRepository, never()).creditCoins(any(), anyInt());
    }

    @Test
    void debitIsBookedAsNegativeAmount() {
        given(userRepository.getReferenceById(USER_ID)).willReturn(new User());

        service.recordDebit(USER_ID, 30, CoinReason.PURCHASE, UUID.randomUUID());

        verify(coinTransactionRepository).save(org.mockito.ArgumentMatchers.<CoinTransaction>argThat(t -> t.getAmount() == -30));
    }
}
