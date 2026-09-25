package edu.stankin.cogoalmain.web.controllers;

import edu.stankin.cogoalmain.config.security.CurrentUserId;
import edu.stankin.cogoalmain.db.entity.enums.ShopItemType;
import edu.stankin.cogoalmain.service.ShopService;
import edu.stankin.cogoalmain.web.dto.shop.PurchaseResultResponse;
import edu.stankin.cogoalmain.web.dto.shop.ShopItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shop/items")
@RequiredArgsConstructor
@Tag(name = "Shop", description = "Profile cosmetics bought with coins")
public class ShopController {

    private final ShopService shopService;

    @GetMapping
    @Operation(summary = "Items on sale, cheapest first, optionally of one type")
    public Page<ShopItemResponse> list(@RequestParam(required = false) ShopItemType type,
                                       @ParameterObject @SortDefault(sort = "price") Pageable pageable) {
        return shopService.listActive(type, pageable);
    }

    @PostMapping("/{id}/purchase")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Buy an item with coins",
            description = "409 if the balance is too low, the item is not on sale, or it is already owned")
    public PurchaseResultResponse purchase(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return shopService.purchase(userId, id);
    }
}
