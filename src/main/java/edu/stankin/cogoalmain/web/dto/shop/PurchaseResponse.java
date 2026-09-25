package edu.stankin.cogoalmain.web.dto.shop;

import java.time.Instant;
import java.util.UUID;

/**
 * @param price price paid at the moment of purchase
 */
public record PurchaseResponse(UUID id, ShopItemSummary shopItem, int price, Instant purchasedAt) {
}
