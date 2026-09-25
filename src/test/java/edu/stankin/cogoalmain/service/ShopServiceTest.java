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
import edu.stankin.cogoalmain.web.mapper.ShopMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static edu.stankin.cogoalmain.support.PactFixtures.user;
import static edu.stankin.cogoalmain.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShopServiceTest {

    private static final int PRICE = 150;

    @Mock
    private ShopItemRepository shopItemRepository;
    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CoinService coinService;

    private ShopService service;

    private final User alice = user("alice");
    private ShopItem item;

    @BeforeEach
    void setUp() {
        service = new ShopService(shopItemRepository, purchaseRepository, userRepository, coinService,
                Mappers.getMapper(ShopMapper.class));

        item = withId(new ShopItem());
        item.setName("Cat");
        item.setItemType(ShopItemType.AVATAR);
        item.setPrice(PRICE);

        given(userRepository.findForUpdate(alice.getId())).willReturn(Optional.of(alice));
        given(shopItemRepository.findById(item.getId())).willReturn(Optional.of(item));
        given(purchaseRepository.save(any())).willAnswer(invocation -> withId(invocation.<Purchase>getArgument(0)));
    }

    @Test
    void purchaseDebitsCoinsAndRecordsPriceAndHistory() {
        given(userRepository.debitCoins(alice.getId(), PRICE)).willReturn(1);
        given(userRepository.findCoinsById(alice.getId())).willReturn(50);

        PurchaseResultResponse result = service.purchase(alice.getId(), item.getId());

        assertThat(result.purchase().price()).isEqualTo(PRICE);
        assertThat(result.purchase().shopItem().id()).isEqualTo(item.getId());
        assertThat(result.coinsLeft()).isEqualTo(50);
        verify(coinService).recordDebit(alice.getId(), PRICE, CoinReason.PURCHASE, result.purchase().id());
    }

    @Test
    void notEnoughCoinsIsConflictAndNothingIsRecorded() {
        given(userRepository.debitCoins(alice.getId(), PRICE)).willReturn(0);

        assertThatThrownBy(() -> service.purchase(alice.getId(), item.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Not enough coins");
        verify(purchaseRepository, never()).save(any());
        verifyNoInteractions(coinService);
    }

    @Test
    void itemNoLongerOnSaleCannotBeBought() {
        item.setActive(false);

        assertThatThrownBy(() -> service.purchase(alice.getId(), item.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no longer on sale");
        verify(userRepository, never()).debitCoins(any(), anyInt());
    }

    @Test
    void ownedItemIsNotBoughtTwice() {
        given(purchaseRepository.existsByUserIdAndShopItemId(alice.getId(), item.getId())).willReturn(true);

        assertThatThrownBy(() -> service.purchase(alice.getId(), item.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already own");
        verify(userRepository, never()).debitCoins(any(), anyInt());
    }

    @Test
    void unknownItemIsNotFound() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> service.purchase(alice.getId(), unknown)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void userRowIsLockedBeforeAnyCheck() {
        given(userRepository.findForUpdate(alice.getId())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.purchase(alice.getId(), item.getId()))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Profile");
        verifyNoInteractions(purchaseRepository);
    }
}
