package edu.stankin.cogoalmain.web.mapper;

import edu.stankin.cogoalmain.db.entity.CoinTransaction;
import edu.stankin.cogoalmain.db.entity.Purchase;
import edu.stankin.cogoalmain.db.entity.ShopItem;
import edu.stankin.cogoalmain.web.dto.shop.CoinTransactionResponse;
import edu.stankin.cogoalmain.web.dto.shop.PurchaseResponse;
import edu.stankin.cogoalmain.web.dto.shop.ShopItemRequest;
import edu.stankin.cogoalmain.web.dto.shop.ShopItemResponse;
import edu.stankin.cogoalmain.web.dto.shop.ShopItemSummary;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(config = CentralMapperConfig.class)
public interface ShopMapper {

    ShopItemResponse toResponse(ShopItem item);

    ShopItemSummary toSummary(ShopItem item);

    ShopItem toEntity(ShopItemRequest request);

    void update(ShopItemRequest request, @MappingTarget ShopItem item);

    /** Requires {@code shopItem} to be loaded. */
    PurchaseResponse toResponse(Purchase purchase);

    CoinTransactionResponse toResponse(CoinTransaction transaction);
}
