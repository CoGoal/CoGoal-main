package edu.stankin.cogoalmain.web.mapper;

import edu.stankin.cogoalmain.db.entity.SupportTicket;
import edu.stankin.cogoalmain.web.dto.support.AdminSupportTicketResponse;
import edu.stankin.cogoalmain.web.dto.support.CreateSupportTicketRequest;
import edu.stankin.cogoalmain.web.dto.support.SupportTicketResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class, uses = GoalMapper.class)
public interface SupportMapper {

    SupportTicketResponse toResponse(SupportTicket ticket);

    /** Requires {@code user} to be loaded. */
    @Mapping(target = "userEmail", source = "user.email")
    AdminSupportTicketResponse toAdminResponse(SupportTicket ticket);

    @Mapping(target = "user", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    SupportTicket toEntity(CreateSupportTicketRequest request);
}
