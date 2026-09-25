package edu.stankin.cogoalmain.web.controllers;

import edu.stankin.cogoalmain.config.security.CurrentUserId;
import edu.stankin.cogoalmain.service.UserService;
import edu.stankin.cogoalmain.web.dto.shop.CoinTransactionResponse;
import edu.stankin.cogoalmain.web.dto.shop.PurchaseResponse;
import edu.stankin.cogoalmain.web.dto.user.MyProfileResponse;
import edu.stankin.cogoalmain.web.dto.user.PublicProfileResponse;
import edu.stankin.cogoalmain.web.dto.user.SetAvatarRequest;
import edu.stankin.cogoalmain.web.dto.user.UpdateProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Own profile, public profiles and user search")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Own profile")
    public MyProfileResponse me(@CurrentUserId UUID userId) {
        return userService.getMyProfile(userId);
    }

    @PatchMapping("/me")
    @Operation(summary = "Update username, first and last name; omitted fields stay unchanged")
    public MyProfileResponse updateMe(@CurrentUserId UUID userId, @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateMyProfile(userId, request);
    }

    @PutMapping("/me/avatar")
    @Operation(summary = "Set a purchased AVATAR item as the profile avatar")
    public MyProfileResponse setAvatar(@CurrentUserId UUID userId, @Valid @RequestBody SetAvatarRequest request) {
        return userService.setAvatar(userId, request.shopItemId());
    }

    @GetMapping("/me/purchases")
    @Operation(summary = "Own purchases, newest first")
    public Page<PurchaseResponse> myPurchases(
            @CurrentUserId UUID userId,
            @ParameterObject @SortDefault(sort = "purchasedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return userService.getPurchases(userId, pageable);
    }

    @GetMapping("/me/coin-transactions")
    @Operation(summary = "Own coin history, newest first")
    public Page<CoinTransactionResponse> myCoinTransactions(
            @CurrentUserId UUID userId,
            @ParameterObject @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return userService.getCoinTransactions(userId, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Public profile of a user (no email and coins)")
    public PublicProfileResponse get(@PathVariable UUID id) {
        return userService.getPublicProfile(id);
    }

    @GetMapping
    @Operation(summary = "Search users by part of the username, e.g. to invite them; the caller is excluded")
    public Page<PublicProfileResponse> search(
            @CurrentUserId UUID userId,
            @RequestParam @NotBlank @Size(max = 50) String search,
            @ParameterObject @SortDefault(sort = "username") Pageable pageable) {
        return userService.search(userId, search, pageable);
    }
}
