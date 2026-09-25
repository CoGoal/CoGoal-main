package edu.stankin.cogoalmain.web.dto.shop;

/**
 * @param coinsLeft the user's balance after the purchase
 */
public record PurchaseResultResponse(PurchaseResponse purchase, int coinsLeft) {
}
