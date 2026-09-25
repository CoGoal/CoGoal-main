package edu.stankin.cogoalmain.db.entity;

import edu.stankin.cogoalmain.db.entity.enums.CoinReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Coin balance change: positive amount is a credit, negative is a debit.
 * {@code (reason, referenceId)} is unique, which makes crediting and debiting idempotent.
 */
@Getter
@Setter
@Entity
@Table(name = "coin_transaction")
public class CoinTransaction extends GeneratedIdEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 50)
    private CoinReason reason;

    /** Goal id for GOAL_COMPLETED, purchase id for PURCHASE. */
    @Column(name = "reference_id")
    private UUID referenceId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
