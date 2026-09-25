package edu.stankin.cogoalmain.service;

import edu.stankin.cogoalmain.db.entity.CoinTransaction;
import edu.stankin.cogoalmain.db.entity.enums.CoinReason;
import edu.stankin.cogoalmain.db.repo.CoinTransactionRepository;
import edu.stankin.cogoalmain.db.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Coin balance changes. Every change is written to {@code coin_transaction}; {@code (reason, referenceId)} is
 * unique, so the same payout or purchase is never booked twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoinService {

    private final CoinTransactionRepository coinTransactionRepository;
    private final UserRepository userRepository;

    /**
     * Credits coins once per {@code (reason, referenceId)}; a repeated call does nothing.
     *
     * @return {@code true} if the coins were credited now
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean credit(UUID userId, int amount, CoinReason reason, UUID referenceId) {
        if (coinTransactionRepository.existsByReasonAndReferenceId(reason, referenceId)) {
            log.info("Coins for {} {} were already credited", reason, referenceId);
            return false;
        }
        book(userId, amount, reason, referenceId);
        userRepository.creditCoins(userId, amount);
        return true;
    }

    /**
     * Records a debit that the caller has already applied with {@link UserRepository#debitCoins}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordDebit(UUID userId, int amount, CoinReason reason, UUID referenceId) {
        book(userId, -amount, reason, referenceId);
    }

    private void book(UUID userId, int signedAmount, CoinReason reason, UUID referenceId) {
        CoinTransaction transaction = new CoinTransaction();
        transaction.setUser(userRepository.getReferenceById(userId));
        transaction.setAmount(signedAmount);
        transaction.setReason(reason);
        transaction.setReferenceId(referenceId);
        coinTransactionRepository.save(transaction);
    }
}
