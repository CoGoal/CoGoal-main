package edu.stankin.cogoalmain.web.mapper;

import edu.stankin.cogoalmain.db.entity.User;
import edu.stankin.cogoalmain.web.dto.internal.InternalUserRequest;
import edu.stankin.cogoalmain.web.dto.internal.InternalUserResponse;
import edu.stankin.cogoalmain.web.dto.user.MyProfileResponse;
import edu.stankin.cogoalmain.web.dto.user.PublicProfileResponse;
import edu.stankin.cogoalmain.web.dto.user.UpdateProfileRequest;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(config = CentralMapperConfig.class)
public interface UserMapper {

    @Mapping(target = "firstName", ignore = true)
    @Mapping(target = "lastName", ignore = true)
    @Mapping(target = "avatarUrl", ignore = true)
    @Mapping(target = "activeAvatarItem", ignore = true)
    @Mapping(target = "coins", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    User toEntity(InternalUserRequest request);

    InternalUserResponse toInternalResponse(User user);

    /** Requires {@code activeAvatarItem} to be loaded. */
    @Mapping(target = "activeAvatar", source = "activeAvatarItem")
    MyProfileResponse toMyProfile(User user);

    // Only the id of the avatar item is read, so the lazy association is not loaded
    @Mapping(target = "activeAvatarItemId", source = "activeAvatarItem.id")
    PublicProfileResponse toPublicProfile(User user);

    /** PATCH: {@code null} fields of the request leave the profile unchanged. */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "avatarUrl", ignore = true)
    @Mapping(target = "activeAvatarItem", ignore = true)
    @Mapping(target = "coins", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateProfile(UpdateProfileRequest request, @MappingTarget User user);
}
