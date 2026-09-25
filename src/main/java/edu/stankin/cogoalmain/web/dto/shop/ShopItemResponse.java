package edu.stankin.cogoalmain.web.dto.shop;

import edu.stankin.cogoalmain.db.entity.enums.ShopItemType;

import java.util.UUID;

public record ShopItemResponse(
        UUID id,
        String name,
        String description,
        int price,
        ShopItemType itemType,
        boolean active
) {
}
