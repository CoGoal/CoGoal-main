package edu.stankin.cogoalmain.db.repo;

import edu.stankin.cogoalmain.db.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Page<Category> findByActiveTrue(Pageable pageable);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);
}
