package edu.stankin.cogoalmain.db.repo;

import edu.stankin.cogoalmain.db.entity.Proof;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProofRepository extends JpaRepository<Proof, UUID> {

    List<Proof> findByCheckInIdOrderByUploadedAt(UUID checkInId);

    /** Only the pact id, so the pact can be locked before the proof itself is loaded. */
    @Query("select p.checkIn.participant.pact.id from Proof p where p.id = :id")
    Optional<UUID> findPactIdById(@Param("id") UUID id);

    /** Proof with its check-in and the check-in's author, for access checks. */
    @Query("select p from Proof p join fetch p.checkIn c join fetch c.participant where p.id = :id")
    Optional<Proof> findWithCheckInById(@Param("id") UUID id);
}
