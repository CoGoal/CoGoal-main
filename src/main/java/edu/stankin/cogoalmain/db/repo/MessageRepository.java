package edu.stankin.cogoalmain.db.repo;

import edu.stankin.cogoalmain.db.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    /** Newest messages of the pact chat; the page size is the limit. */
    @Query("""
            select m from Message m join fetch m.sender
            where m.pact.id = :pactId
            order by m.createdAt desc, m.id desc
            """)
    List<Message> findLatest(@Param("pactId") UUID pactId, Pageable limit);

    /** Messages older than the cursor, newest first; the page size is the limit. */
    @Query("""
            select m from Message m join fetch m.sender
            where m.pact.id = :pactId and m.createdAt < :before
            order by m.createdAt desc, m.id desc
            """)
    List<Message> findBefore(@Param("pactId") UUID pactId, @Param("before") Instant before, Pageable limit);
}
