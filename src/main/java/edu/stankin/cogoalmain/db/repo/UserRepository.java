package edu.stankin.cogoalmain.db.repo;

import edu.stankin.cogoalmain.db.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);

    /**
     * Locks the user's row for the rest of the transaction, so the same user's purchases run one after
     * another (otherwise two parallel requests could both buy an item the user does not own yet).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findForUpdate(@Param("id") UUID id);

    /** Current balance straight from the database (a User loaded earlier may be stale after a coin UPDATE). */
    @Query("select u.coins from User u where u.id = :id")
    int findCoinsById(@Param("id") UUID id);

    /** Profile together with the active avatar item, for "my profile" responses. */
    @EntityGraph(attributePaths = "activeAvatarItem")
    Optional<User> findWithAvatarById(UUID id);

    /** Username search for invitations; the searching user is excluded. {@code %} and {@code _} are escaped. */
    Page<User> findByUsernameContainingIgnoreCaseAndIdNot(String username, UUID excludedId, Pageable pageable);

    /*
     * Coin balance changes are atomic UPDATEs so concurrent purchases and payouts cannot lose updates.
     * They deliberately do NOT clear the persistence context: clearing would silently drop other changes
     * made later in the same transaction (e.g. finishing the pact after a payout). The flip side: a User
     * already loaded in the transaction still shows the old balance, so re-read it if the balance is needed.
     */

    /**
     * Atomically debits coins if the balance is sufficient.
     *
     * @return 1 if debited, 0 if the user does not exist or has fewer coins than {@code amount}
     */
    @Modifying(flushAutomatically = true)
    @Query("update User u set u.coins = u.coins - :amount where u.id = :id and u.coins >= :amount")
    int debitCoins(@Param("id") UUID id, @Param("amount") int amount);

    @Modifying(flushAutomatically = true)
    @Query("update User u set u.coins = u.coins + :amount where u.id = :id")
    int creditCoins(@Param("id") UUID id, @Param("amount") int amount);
}
