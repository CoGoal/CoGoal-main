package edu.stankin.cogoalmain.web.dto.shop;

import edu.stankin.cogoalmain.db.entity.enums.ShopItemType;

import java.util.UUID;

public record ShopItemSummary(UUID id, String name, ShopItemType itemType) {
}
