package edu.stankin.cogoalmain.web.dto.shop;

import edu.stankin.cogoalmain.db.entity.enums.CoinReason;

import java.time.Instant;
import java.util.UUID;

/**
 * @param amount      positive for a credit, negative for a debit
 * @param referenceId goal id for GOAL_COMPLETED, purchase id for PURCHASE
 */
public record CoinTransactionResponse(UUID id, int amount, CoinReason reason, UUID referenceId, Instant createdAt) {
}
