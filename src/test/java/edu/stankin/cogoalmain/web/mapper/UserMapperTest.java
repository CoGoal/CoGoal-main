package edu.stankin.cogoalmain.web.mapper;

import edu.stankin.cogoalmain.db.entity.ShopItem;
import edu.stankin.cogoalmain.db.entity.User;
import edu.stankin.cogoalmain.db.entity.enums.ShopItemType;
import edu.stankin.cogoalmain.web.dto.user.MyProfileResponse;
import edu.stankin.cogoalmain.web.dto.user.PublicProfileResponse;
import edu.stankin.cogoalmain.web.dto.user.UpdateProfileRequest;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static edu.stankin.cogoalmain.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private final UserMapper mapper = Mappers.getMapper(UserMapper.class);

    @Test
    void publicProfileHasNoPrivateFields() {
        User user = user();

        PublicProfileResponse profile = mapper.toPublicProfile(user);

        assertThat(profile.id()).isEqualTo(user.getId());
        assertThat(profile.username()).isEqualTo("alice");
        assertThat(profile.activeAvatarItemId()).isEqualTo(user.getActiveAvatarItem().getId());
        assertThat(PublicProfileResponse.class.getRecordComponents())
                .extracting(c -> c.getName())
                .doesNotContain("email", "coins");
    }

    @Test
    void myProfileContainsAvatarSummaryAndCoins() {
        User user = user();

        MyProfileResponse profile = mapper.toMyProfile(user);

        assertThat(profile.email()).isEqualTo("alice@example.com");
        assertThat(profile.coins()).isEqualTo(150);
        assertThat(profile.activeAvatar().name()).isEqualTo("Cat");
        assertThat(profile.activeAvatar().itemType()).isEqualTo(ShopItemType.AVATAR);
    }

    @Test
    void patchLeavesNullFieldsUnchanged() {
        User user = user();

        mapper.updateProfile(new UpdateProfileRequest(null, "Alicia", null), user);

        assertThat(user.getUsername()).isEqualTo("alice");
        assertThat(user.getFirstName()).isEqualTo("Alicia");
        assertThat(user.getLastName()).isEqualTo("Smith");
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getCoins()).isEqualTo(150);
    }

    private static User user() {
        ShopItem avatar = withId(new ShopItem());
        avatar.setName("Cat");
        avatar.setItemType(ShopItemType.AVATAR);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setFirstName("Alice");
        user.setLastName("Smith");
        user.setCoins(150);
        user.setActiveAvatarItem(avatar);
        return user;
    }
}
