package edu.stankin.cogoalmain.web.controllers;

import edu.stankin.cogoalmain.service.CategoryService;
import edu.stankin.cogoalmain.service.CharityService;
import edu.stankin.cogoalmain.web.dto.catalog.CategoryResponse;
import edu.stankin.cogoalmain.web.dto.catalog.CharityResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.SortDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Catalog", description = "Goal categories and charities to choose from")
public class CatalogController {

    private final CategoryService categoryService;
    private final CharityService charityService;

    @GetMapping("/categories")
    @Operation(summary = "Active goal categories")
    public Page<CategoryResponse> categories(@ParameterObject @SortDefault(sort = "name") Pageable pageable) {
        return categoryService.listActive(pageable);
    }

    @GetMapping("/charities")
    @Operation(summary = "Active charities a pact can donate failed deposits to")
    public Page<CharityResponse> charities(@ParameterObject @SortDefault(sort = "name") Pageable pageable) {
        return charityService.listActive(pageable);
    }
}
