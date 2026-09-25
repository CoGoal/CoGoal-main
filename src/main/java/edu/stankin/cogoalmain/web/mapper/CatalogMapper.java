package edu.stankin.cogoalmain.web.mapper;

import edu.stankin.cogoalmain.db.entity.Category;
import edu.stankin.cogoalmain.db.entity.Charity;
import edu.stankin.cogoalmain.web.dto.catalog.CategoryRequest;
import edu.stankin.cogoalmain.web.dto.catalog.CategoryResponse;
import edu.stankin.cogoalmain.web.dto.catalog.CharityRequest;
import edu.stankin.cogoalmain.web.dto.catalog.CharityResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

/**
 * Categories and charities. A {@code null} {@code active} in a request keeps the entity's value
 * (MapStruct skips null wrappers when the target is a primitive).
 */
@Mapper(config = CentralMapperConfig.class)
public interface CatalogMapper {

    CategoryResponse toResponse(Category category);

    Category toEntity(CategoryRequest request);

    void update(CategoryRequest request, @MappingTarget Category category);

    CharityResponse toResponse(Charity charity);

    Charity toEntity(CharityRequest request);

    void update(CharityRequest request, @MappingTarget Charity charity);
}
