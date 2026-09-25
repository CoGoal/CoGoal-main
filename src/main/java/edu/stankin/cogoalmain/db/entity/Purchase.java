package edu.stankin.cogoalmain.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "purchase")
public class Purchase extends GeneratedIdEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shop_item_id", nullable = false)
    private ShopItem shopItem;

    /** Price at the moment of purchase. */
    @Column(name = "price", nullable = false)
    private int price;

    @CreationTimestamp
    @Column(name = "purchased_at", nullable = false, updatable = false)
    private Instant purchasedAt;
}
