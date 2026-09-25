package edu.stankin.cogoalmain.service;

import edu.stankin.cogoalmain.db.entity.Purchase;
import edu.stankin.cogoalmain.db.entity.ShopItem;
import edu.stankin.cogoalmain.db.entity.User;
import edu.stankin.cogoalmain.db.entity.enums.CoinReason;
import edu.stankin.cogoalmain.db.entity.enums.ShopItemType;
import edu.stankin.cogoalmain.db.repo.PurchaseRepository;
import edu.stankin.cogoalmain.db.repo.ShopItemRepository;
import edu.stankin.cogoalmain.db.repo.UserRepository;
import edu.stankin.cogoalmain.exception.BusinessRuleException;
import edu.stankin.cogoalmain.exception.NotFoundException;
import edu.stankin.cogoalmain.web.dto.shop.PurchaseResultResponse;
import edu.stankin.cogoalmain.web.dto.shop.ShopItemResponse;
import edu.stankin.cogoalmain.web.mapper.ShopMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Buying profile cosmetics with coins.
 */
@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopItemRepository shopItemRepository;
    private final PurchaseRepository purchaseRepository;
    private final UserRepository userRepository;
    private final CoinService coinService;
    private final ShopMapper shopMapper;

    /** Items on sale, optionally of one type. */
    @Transactional(readOnly = true)
    public Page<ShopItemResponse> listActive(ShopItemType type, Pageable pageable) {
        Page<ShopItem> items = type == null
                ? shopItemRepository.findByActiveTrue(pageable)
                : shopItemRepository.findByActiveTrueAndItemType(type, pageable);
        return items.map(shopMapper::toResponse);
    }

    /**
     * Buys an item in one transaction: the coins are debited atomically (only if the balance is enough),
     * the purchase keeps the price paid, and the debit is recorded in the coin history.
     *
     * @throws NotFoundException     if the item or the user's profile does not exist
     * @throws BusinessRuleException if the item is not on sale, already owned, or the balance is too low
     */
    @Transactional
    public PurchaseResultResponse purchase(UUID userId, UUID itemId) {
        User user = userRepository.findForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("Profile of user " + userId + " has not been created yet"));
        ShopItem item = shopItemRepository.findById(itemId).orElseThrow(() -> NotFoundException.of("Shop item", itemId));

        if (!item.isActive()) {
            throw new BusinessRuleException("This item is no longer on sale");
        }
        // Cosmetics are owned, not consumed, so buying the same one twice would only waste coins
        if (purchaseRepository.existsByUserIdAndShopItemId(userId, itemId)) {
            throw new BusinessRuleException("You already own this item");
        }
        int price = item.getPrice();
        if (userRepository.debitCoins(userId, price) == 0) {
            throw new BusinessRuleException("Not enough coins: the item costs " + price);
        }

        Purchase purchase = new Purchase();
        purchase.setUser(user);
        purchase.setShopItem(item);
        purchase.setPrice(price);
        purchaseRepository.save(purchase);
        coinService.recordDebit(userId, price, CoinReason.PURCHASE, purchase.getId());

        return new PurchaseResultResponse(shopMapper.toResponse(purchase), userRepository.findCoinsById(userId));
    }
}
