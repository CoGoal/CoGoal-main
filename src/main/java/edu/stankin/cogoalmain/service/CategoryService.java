package edu.stankin.cogoalmain.service;

import edu.stankin.cogoalmain.db.entity.Category;
import edu.stankin.cogoalmain.db.repo.CategoryRepository;
import edu.stankin.cogoalmain.db.repo.GoalRepository;
import edu.stankin.cogoalmain.exception.BusinessRuleException;
import edu.stankin.cogoalmain.exception.NotFoundException;
import edu.stankin.cogoalmain.web.dto.catalog.CategoryRequest;
import edu.stankin.cogoalmain.web.dto.catalog.CategoryResponse;
import edu.stankin.cogoalmain.web.dto.catalog.DeletionResponse;
import edu.stankin.cogoalmain.web.mapper.CatalogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final GoalRepository goalRepository;
    private final CatalogMapper catalogMapper;

    @Transactional(readOnly = true)
    public Page<CategoryResponse> listActive(Pageable pageable) {
        return categoryRepository.findByActiveTrue(pageable).map(catalogMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<CategoryResponse> listAll(Pageable pageable) {
        return categoryRepository.findAll(pageable).map(catalogMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(UUID id) {
        return catalogMapper.toResponse(find(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByName(request.name())) {
            throw duplicateName(request.name());
        }
        return catalogMapper.toResponse(categoryRepository.save(catalogMapper.toEntity(request)));
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        Category category = find(id);
        if (categoryRepository.existsByNameAndIdNot(request.name(), id)) {
            throw duplicateName(request.name());
        }
        catalogMapper.update(request, category);
        return catalogMapper.toResponse(category);
    }

    /** Deletes the category, or only deactivates it if goals refer to it. */
    @Transactional
    public DeletionResponse delete(UUID id) {
        Category category = find(id);
        if (goalRepository.existsByCategoryId(id)) {
            category.setActive(false);
            return DeletionResponse.deactivated();
        }
        categoryRepository.delete(category);
        return DeletionResponse.deleted();
    }

    /**
     * Active category for a new goal.
     *
     * @throws NotFoundException     if it does not exist
     * @throws BusinessRuleException if it is deactivated
     */
    @Transactional(readOnly = true)
    public Category requireActive(UUID id) {
        Category category = find(id);
        if (!category.isActive()) {
            throw new BusinessRuleException("Category is not active");
        }
        return category;
    }

    private Category find(UUID id) {
        return categoryRepository.findById(id).orElseThrow(() -> NotFoundException.of("Category", id));
    }

    private static BusinessRuleException duplicateName(String name) {
        return new BusinessRuleException("Category with name '" + name + "' already exists");
    }
}
