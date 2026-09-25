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
import edu.stankin.cogoalmain.web.dto.user.MyProfileResponse;
import edu.stankin.cogoalmain.web.mapper.ShopMapper;
import edu.stankin.cogoalmain.web.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static edu.stankin.cogoalmain.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;
    @Mock
    private ShopItemRepository shopItemRepository;
    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private CoinTransactionRepository coinTransactionRepository;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, shopItemRepository, purchaseRepository, coinTransactionRepository,
                Mappers.getMapper(UserMapper.class), Mappers.getMapper(ShopMapper.class));
    }

    @Nested
    class Register {

        private final InternalUserRequest request = new InternalUserRequest(USER_ID, "a@example.com", "alice");

        @Test
        void createsNewProfile() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.empty());
            given(userRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

            UserService.Registration registration = service.register(request);

            assertThat(registration.created()).isTrue();
            assertThat(registration.user().id()).isEqualTo(USER_ID);
        }

        @Test
        void repeatedCallReturnsExistingProfileUnchanged() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));

            UserService.Registration registration = service.register(request);

            assertThat(registration.created()).isFalse();
            verify(userRepository, never()).save(any());
        }

        @Test
        void rejectsEmailOfAnotherUser() {
            given(userRepository.findById(USER_ID)).willReturn(Optional.empty());
            given(userRepository.existsByEmail("a@example.com")).willReturn(true);

            assertThatThrownBy(() -> service.register(request)).isInstanceOf(BusinessRuleException.class);
        }
    }

    @Nested
    class SetAvatar {

        private final ShopItem avatar = item(ShopItemType.AVATAR);

        @Test
        void setsPurchasedAvatar() {
            User user = user();
            given(userRepository.findWithAvatarById(USER_ID)).willReturn(Optional.of(user));
            given(shopItemRepository.findById(avatar.getId())).willReturn(Optional.of(avatar));
            given(purchaseRepository.existsByUserIdAndShopItemId(USER_ID, avatar.getId())).willReturn(true);

            MyProfileResponse profile = service.setAvatar(USER_ID, avatar.getId());

            assertThat(user.getActiveAvatarItem()).isEqualTo(avatar);
            assertThat(profile.activeAvatar().id()).isEqualTo(avatar.getId());
        }

        @Test
        void rejectsItemThatWasNotPurchased() {
            given(userRepository.findWithAvatarById(USER_ID)).willReturn(Optional.of(user()));
            given(shopItemRepository.findById(avatar.getId())).willReturn(Optional.of(avatar));
            given(purchaseRepository.existsByUserIdAndShopItemId(USER_ID, avatar.getId())).willReturn(false);

            assertThatThrownBy(() -> service.setAvatar(USER_ID, avatar.getId()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("not been purchased");
        }

        @Test
        void rejectsItemOfOtherType() {
            ShopItem frame = item(ShopItemType.FRAME);
            given(userRepository.findWithAvatarById(USER_ID)).willReturn(Optional.of(user()));
            given(shopItemRepository.findById(frame.getId())).willReturn(Optional.of(frame));

            assertThatThrownBy(() -> service.setAvatar(USER_ID, frame.getId()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("AVATAR");
        }

        @Test
        void unknownItemIsNotFound() {
            UUID unknown = UUID.randomUUID();
            given(userRepository.findWithAvatarById(USER_ID)).willReturn(Optional.of(user()));
            given(shopItemRepository.findById(unknown)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.setAvatar(USER_ID, unknown)).isInstanceOf(NotFoundException.class);
        }
    }

    @Test
    void profileNotCreatedYetIsNotFound() {
        given(userRepository.findWithAvatarById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyProfile(USER_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("has not been created");
    }

    private static User user() {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("alice");
        user.setEmail("a@example.com");
        return user;
    }

    private static ShopItem item(ShopItemType type) {
        ShopItem item = withId(new ShopItem());
        item.setName(type.name());
        item.setItemType(type);
        item.setPrice(50);
        return item;
    }
}
