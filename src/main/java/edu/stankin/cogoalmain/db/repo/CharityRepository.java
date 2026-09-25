package edu.stankin.cogoalmain.db.repo;

import edu.stankin.cogoalmain.db.entity.Charity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CharityRepository extends JpaRepository<Charity, UUID> {

    Page<Charity> findByActiveTrue(Pageable pageable);
}
