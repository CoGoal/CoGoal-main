package edu.stankin.cogoalmain.service;

import edu.stankin.cogoalmain.db.entity.ShopItem;
import edu.stankin.cogoalmain.db.repo.PurchaseRepository;
import edu.stankin.cogoalmain.db.repo.ShopItemRepository;
import edu.stankin.cogoalmain.exception.NotFoundException;
import edu.stankin.cogoalmain.web.dto.catalog.DeletionResponse;
import edu.stankin.cogoalmain.web.dto.shop.ShopItemRequest;
import edu.stankin.cogoalmain.web.dto.shop.ShopItemResponse;
import edu.stankin.cogoalmain.web.mapper.ShopMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Admin management of shop items. Buying is in the shop service.
 */
@Service
@RequiredArgsConstructor
public class ShopItemService {

    private final ShopItemRepository shopItemRepository;
    private final PurchaseRepository purchaseRepository;
    private final ShopMapper shopMapper;

    @Transactional(readOnly = true)
    public Page<ShopItemResponse> listAll(Pageable pageable) {
        return shopItemRepository.findAll(pageable).map(shopMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ShopItemResponse get(UUID id) {
        return shopMapper.toResponse(find(id));
    }

    @Transactional
    public ShopItemResponse create(ShopItemRequest request) {
        return shopMapper.toResponse(shopItemRepository.save(shopMapper.toEntity(request)));
    }

    /** A new price applies to future purchases only; past purchases keep the price they were paid. */
    @Transactional
    public ShopItemResponse update(UUID id, ShopItemRequest request) {
        ShopItem item = find(id);
        shopMapper.update(request, item);
        return shopMapper.toResponse(item);
    }

    /** Deletes the item, or only deactivates it (no longer sold) if someone has bought it. */
    @Transactional
    public DeletionResponse delete(UUID id) {
        ShopItem item = find(id);
        if (purchaseRepository.existsByShopItemId(id)) {
            item.setActive(false);
            return DeletionResponse.deactivated();
        }
        shopItemRepository.delete(item);
        return DeletionResponse.deleted();
    }

    private ShopItem find(UUID id) {
        return shopItemRepository.findById(id).orElseThrow(() -> NotFoundException.of("Shop item", id));
    }
}
