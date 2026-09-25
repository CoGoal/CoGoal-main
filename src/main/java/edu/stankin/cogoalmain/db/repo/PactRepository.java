package edu.stankin.cogoalmain.db.repo;

import edu.stankin.cogoalmain.db.entity.Pact;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PactRepository extends JpaRepository<Pact, UUID> {

    boolean existsByCharityId(UUID charityId);

    /**
     * Locks the pact row for the rest of the transaction. Every change of pact membership or status
     * (join, leave, start, cancel, invitations, deposit events) goes through this lock, so e.g. a join
     * cannot slip in while the pact is being started.
     */
    // No fetch joins on purpose: FOR UPDATE on a join may also lock the goal and charity rows, and a charity
    // is shared by many pacts, which would make unrelated pacts wait for each other
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pact p where p.id = :id")
    Optional<Pact> findForUpdate(@Param("id") UUID id);

    /** Pact with the creator's goal and the charity, for read-only views. */
    @Query("select p from Pact p join fetch p.goal join fetch p.charity where p.id = :id")
    Optional<Pact> findDetailedById(@Param("id") UUID id);
}
