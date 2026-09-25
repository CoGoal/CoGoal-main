package edu.stankin.cogoalmain.web.mapper;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.MapperConfig;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * Shared settings of all MapStruct mappers.
 * <p>
 * {@code unmappedTargetPolicy = ERROR}: a target field that is neither mapped nor explicitly ignored
 * fails the build, so a new DTO or entity field cannot be silently left empty.
 * Relations (category, goal, user, ...) are loaded by services and set by hand; mappers ignore them.
 */
@MapperConfig(
        componentModel = MappingConstants.ComponentModel.SPRING,
        // Mappers that use other mappers get them through the constructor, so unit tests can build them by hand
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface CentralMapperConfig {
}
