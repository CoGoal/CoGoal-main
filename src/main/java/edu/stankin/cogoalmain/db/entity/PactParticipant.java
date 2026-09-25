package edu.stankin.cogoalmain.db.entity;

import edu.stankin.cogoalmain.db.entity.enums.DepositStatus;
import edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus;
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

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "pact_participant")
public class PactParticipant extends GeneratedIdEntity {

    public static final String DEFAULT_CURRENCY = "RUB";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pact_id", nullable = false)
    private Pact pact;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The participant's own goal within this pact. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goal_id", nullable = false)
    private Goal goal;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ParticipantStatus status;

    @Column(name = "deposit_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "deposit_currency", nullable = false, length = 3)
    private String depositCurrency = DEFAULT_CURRENCY;

    @Enumerated(EnumType.STRING)
    @Column(name = "deposit_status", nullable = false, length = 50)
    private DepositStatus depositStatus;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    /** When the reminder about the goal's final deadline was sent; prevents duplicates. */
    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;
}
