package edu.stankin.cogoalmain.web.controllers.admin;

import edu.stankin.cogoalmain.service.CategoryService;
import edu.stankin.cogoalmain.web.dto.catalog.CategoryRequest;
import edu.stankin.cogoalmain.web.dto.catalog.CategoryResponse;
import edu.stankin.cogoalmain.web.dto.catalog.DeletionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
@Tag(name = "Admin: categories")
public class CategoryAdminController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "All categories, including inactive")
    public Page<CategoryResponse> list(@ParameterObject @SortDefault(sort = "name") Pageable pageable) {
        return categoryService.listAll(pageable);
    }

    @GetMapping("/{id}")
    public CategoryResponse get(@PathVariable UUID id) {
        return categoryService.get(id);
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse created = categoryService.create(request);
        return ResponseEntity.created(AdminLocations.of(created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace the category; a missing 'active' keeps the current value")
    public CategoryResponse update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return categoryService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete the category, or deactivate it if goals refer to it")
    public DeletionResponse delete(@PathVariable UUID id) {
        return categoryService.delete(id);
    }
}
