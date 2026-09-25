package edu.stankin.cogoalmain.web.controllers.admin;

import edu.stankin.cogoalmain.service.ShopItemService;
import edu.stankin.cogoalmain.web.dto.catalog.DeletionResponse;
import edu.stankin.cogoalmain.web.dto.shop.ShopItemRequest;
import edu.stankin.cogoalmain.web.dto.shop.ShopItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/shop-items")
@RequiredArgsConstructor
@Tag(name = "Admin: shop items")
public class ShopItemAdminController {

    private final ShopItemService shopItemService;

    @GetMapping
    @Operation(summary = "All shop items, including inactive")
    public Page<ShopItemResponse> list(@ParameterObject @SortDefault(sort = "name") Pageable pageable) {
        return shopItemService.listAll(pageable);
    }

    @GetMapping("/{id}")
    public ShopItemResponse get(@PathVariable UUID id) {
        return shopItemService.get(id);
    }

    @PostMapping
    public ResponseEntity<ShopItemResponse> create(@Valid @RequestBody ShopItemRequest request) {
        ShopItemResponse created = shopItemService.create(request);
        return ResponseEntity.created(AdminLocations.of(created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace the item; a missing 'active' keeps the current value. "
            + "A new price does not change past purchases")
    public ShopItemResponse update(@PathVariable UUID id, @Valid @RequestBody ShopItemRequest request) {
        return shopItemService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete the item, or deactivate it (stop selling) if someone has bought it")
    public DeletionResponse delete(@PathVariable UUID id) {
        return shopItemService.delete(id);
    }
}
