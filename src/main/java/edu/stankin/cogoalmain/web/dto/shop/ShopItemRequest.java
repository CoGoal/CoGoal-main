package edu.stankin.cogoalmain.web.dto.shop;

import edu.stankin.cogoalmain.db.entity.enums.ShopItemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Admin create/replace of a shop item.
 *
 * @param active {@code null} keeps the current value (new items are active)
 */
public record ShopItemRequest(
        @NotBlank @Size(max = 200) String name,
        String description,
        @NotNull @Positive Integer price,
        @NotNull ShopItemType itemType,
        Boolean active
) {
}
