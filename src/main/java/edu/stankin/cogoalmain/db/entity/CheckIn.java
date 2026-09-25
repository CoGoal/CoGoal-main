package edu.stankin.cogoalmain.db.entity;

import edu.stankin.cogoalmain.db.entity.enums.CheckInStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A participant's report on a milestone, or on the whole goal when {@code milestone} is null.
 */
@Getter
@Setter
@Entity
@Table(name = "check_in")
public class CheckIn extends GeneratedIdEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    private PactParticipant participant;

    /** Null means the final report on the goal. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "milestone_id")
    private Milestone milestone;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private CheckInStatus status;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    /** Read-only view; proofs are created and removed through their repository. */
    @OneToMany(mappedBy = "checkIn")
    private List<Proof> proofs = new ArrayList<>();

    /** Read-only view; reviews are created through their repository. */
    @OneToMany(mappedBy = "checkIn")
    private List<Review> reviews = new ArrayList<>();
}
