package edu.stankin.cogoalmain.db.repo;

import edu.stankin.cogoalmain.db.entity.ShopItem;
import edu.stankin.cogoalmain.db.entity.enums.ShopItemType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ShopItemRepository extends JpaRepository<ShopItem, UUID> {

    Page<ShopItem> findByActiveTrue(Pageable pageable);

    Page<ShopItem> findByActiveTrueAndItemType(ShopItemType itemType, Pageable pageable);
}
