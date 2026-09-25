package edu.stankin.cogoalmain.service;

import edu.stankin.cogoalmain.db.entity.Category;
import edu.stankin.cogoalmain.db.repo.CategoryRepository;
import edu.stankin.cogoalmain.db.repo.GoalRepository;
import edu.stankin.cogoalmain.exception.BusinessRuleException;
import edu.stankin.cogoalmain.web.dto.catalog.CategoryRequest;
import edu.stankin.cogoalmain.web.dto.catalog.DeletionResponse;
import edu.stankin.cogoalmain.web.mapper.CatalogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static edu.stankin.cogoalmain.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Delete-or-deactivate and uniqueness rules; charities and shop items follow the same pattern.
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private GoalRepository goalRepository;

    private CategoryService service;

    @BeforeEach
    void setUp() {
        service = new CategoryService(categoryRepository, goalRepository, Mappers.getMapper(CatalogMapper.class));
    }

    @Test
    void deletesUnusedCategory() {
        Category category = category();
        given(categoryRepository.findById(category.getId())).willReturn(Optional.of(category));
        given(goalRepository.existsByCategoryId(category.getId())).willReturn(false);

        assertThat(service.delete(category.getId()).outcome()).isEqualTo(DeletionResponse.Outcome.DELETED);
        verify(categoryRepository).delete(category);
    }

    @Test
    void deactivatesCategoryUsedByGoals() {
        Category category = category();
        given(categoryRepository.findById(category.getId())).willReturn(Optional.of(category));
        given(goalRepository.existsByCategoryId(category.getId())).willReturn(true);

        assertThat(service.delete(category.getId()).outcome()).isEqualTo(DeletionResponse.Outcome.DEACTIVATED);
        assertThat(category.isActive()).isFalse();
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void rejectsDuplicateNameOnCreate() {
        given(categoryRepository.existsByName("Sport")).willReturn(true);

        assertThatThrownBy(() -> service.create(new CategoryRequest("Sport", null, null)))
                .isInstanceOf(BusinessRuleException.class);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateWithoutActiveFlagKeepsCurrentValue() {
        Category category = category();
        category.setActive(false);
        given(categoryRepository.findById(category.getId())).willReturn(Optional.of(category));

        service.update(category.getId(), new CategoryRequest("Health", "new description", null));

        assertThat(category.getName()).isEqualTo("Health");
        assertThat(category.getDescription()).isEqualTo("new description");
        assertThat(category.isActive()).isFalse();
    }

    @Test
    void inactiveCategoryCannotBeUsedForNewGoals() {
        Category category = category();
        category.setActive(false);
        given(categoryRepository.findById(category.getId())).willReturn(Optional.of(category));

        assertThatThrownBy(() -> service.requireActive(category.getId()))
                .isInstanceOf(BusinessRuleException.class);
    }

    private static Category category() {
        Category category = withId(new Category());
        category.setName("Sport");
        return category;
    }
}
