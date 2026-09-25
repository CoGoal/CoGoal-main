package edu.stankin.cogoalmain.db.entity;

import edu.stankin.cogoalmain.db.entity.enums.ProofType;
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

/**
 * Evidence attached to a check-in: an uploaded file ({@code fileUrl}) or a link ({@code externalUrl}).
 * The database requires at least one of the two.
 */
@Getter
@Setter
@Entity
@Table(name = "proof")
public class Proof extends GeneratedIdEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "checkin_id", nullable = false)
    private CheckIn checkIn;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private ProofType type;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "external_url", length = 500)
    private String externalUrl;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;
}
