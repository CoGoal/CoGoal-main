package edu.stankin.cogoalmain.db.repo;

import edu.stankin.cogoalmain.db.entity.Purchase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {

    @EntityGraph(attributePaths = "shopItem")
    Page<Purchase> findByUserId(UUID userId, Pageable pageable);

    boolean existsByUserIdAndShopItemId(UUID userId, UUID shopItemId);

    boolean existsByShopItemId(UUID shopItemId);
}
