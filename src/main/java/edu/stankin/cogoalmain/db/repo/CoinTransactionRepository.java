package edu.stankin.cogoalmain.db.repo;

import edu.stankin.cogoalmain.db.entity.CoinTransaction;
import edu.stankin.cogoalmain.db.entity.enums.CoinReason;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CoinTransactionRepository extends JpaRepository<CoinTransaction, UUID> {

    Page<CoinTransaction> findByUserId(UUID userId, Pageable pageable);

    boolean existsByReasonAndReferenceId(CoinReason reason, UUID referenceId);
}
