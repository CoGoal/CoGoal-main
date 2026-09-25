package edu.stankin.cogoalmain.service;

import edu.stankin.cogoalmain.db.entity.ShopItem;
import edu.stankin.cogoalmain.db.entity.User;
import edu.stankin.cogoalmain.db.entity.enums.ShopItemType;
import edu.stankin.cogoalmain.db.repo.CoinTransactionRepository;
import edu.stankin.cogoalmain.db.repo.PurchaseRepository;
import edu.stankin.cogoalmain.db.repo.ShopItemRepository;
import edu.stankin.cogoalmain.db.repo.UserRepository;
import edu.stankin.cogoalmain.exception.BusinessRuleException;
import edu.stankin.cogoalmain.exception.NotFoundException;
import edu.stankin.cogoalmain.web.dto.internal.InternalUserRequest;
import edu.stankin.cogoalmain.web.dto.internal.InternalUserResponse;
import edu.stankin.cogoalmain.web.dto.shop.CoinTransactionResponse;
import edu.stankin.cogoalmain.web.dto.shop.PurchaseResponse;
import edu.stankin.cogoalmain.web.dto.user.MyProfileResponse;
import edu.stankin.cogoalmain.web.dto.user.PublicProfileResponse;
import edu.stankin.cogoalmain.web.dto.user.UpdateProfileRequest;
import edu.stankin.cogoalmain.web.mapper.ShopMapper;
import edu.stankin.cogoalmain.web.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ShopItemRepository shopItemRepository;
    private final PurchaseRepository purchaseRepository;
    private final CoinTransactionRepository coinTransactionRepository;
    private final UserMapper userMapper;
    private final ShopMapper shopMapper;

    /**
     * Creates the profile of a user registered in auth-service.
     * <p>
     * Idempotent: if a profile with this id already exists it is returned unchanged,
     * so auth-service can safely retry the call.
     *
     * @throws BusinessRuleException if another user already has this email
     */
    @Transactional
    public Registration register(InternalUserRequest request) {
        Optional<User> existing = userRepository.findById(request.id());
        if (existing.isPresent()) {
            return new Registration(userMapper.toInternalResponse(existing.get()), false);
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessRuleException("Email is already used by another user");
        }
        User user = userRepository.save(userMapper.toEntity(request));
        return new Registration(userMapper.toInternalResponse(user), true);
    }

    @Transactional(readOnly = true)
    public MyProfileResponse getMyProfile(UUID userId) {
        return userMapper.toMyProfile(findWithAvatar(userId));
    }

    @Transactional
    public MyProfileResponse updateMyProfile(UUID userId, UpdateProfileRequest request) {
        User user = findWithAvatar(userId);
        userMapper.updateProfile(request, user);
        return userMapper.toMyProfile(user);
    }

    /**
     * @throws NotFoundException     if the item does not exist
     * @throws BusinessRuleException if the item is not an avatar or the user has not bought it
     */
    @Transactional
    public MyProfileResponse setAvatar(UUID userId, UUID shopItemId) {
        User user = findWithAvatar(userId);
        ShopItem item = shopItemRepository.findById(shopItemId)
                .orElseThrow(() -> NotFoundException.of("Shop item", shopItemId));

        if (item.getItemType() != ShopItemType.AVATAR) {
            throw new BusinessRuleException("Only an item of type AVATAR can be set as avatar");
        }
        if (!purchaseRepository.existsByUserIdAndShopItemId(userId, shopItemId)) {
            throw new BusinessRuleException("The avatar has not been purchased");
        }

        user.setActiveAvatarItem(item);
        return userMapper.toMyProfile(user);
    }

    @Transactional(readOnly = true)
    public PublicProfileResponse getPublicProfile(UUID userId) {
        return userRepository.findById(userId)
                .map(userMapper::toPublicProfile)
                .orElseThrow(() -> NotFoundException.of("User", userId));
    }

    /** Username search for invitations; the searching user is not in the results. */
    @Transactional(readOnly = true)
    public Page<PublicProfileResponse> search(UUID currentUserId, String query, Pageable pageable) {
        return userRepository.findByUsernameContainingIgnoreCaseAndIdNot(query.trim(), currentUserId, pageable)
                .map(userMapper::toPublicProfile);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseResponse> getPurchases(UUID userId, Pageable pageable) {
        return purchaseRepository.findByUserId(userId, pageable).map(shopMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<CoinTransactionResponse> getCoinTransactions(UUID userId, Pageable pageable) {
        return coinTransactionRepository.findByUserId(userId, pageable).map(shopMapper::toResponse);
    }

    // The token is valid but auth-service has not created the profile yet (POST /internal/users)
    private User findWithAvatar(UUID userId) {
        return userRepository.findWithAvatarById(userId)
                .orElseThrow(() -> new NotFoundException("Profile of user " + userId + " has not been created yet"));
    }

    /**
     * @param created {@code false} when the profile already existed
     */
    public record Registration(InternalUserResponse user, boolean created) {
    }
}
