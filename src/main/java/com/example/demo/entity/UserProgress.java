package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "player_progress", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "city_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private City city;

    @Builder.Default
    private Boolean unlocked = false;
    @Builder.Default
    private Boolean bossUnlocked = false;
    @Builder.Default
    private Boolean bossCompleted = false;
    @Builder.Default
    private Boolean badgeUnlocked = false;

    private java.time.LocalDateTime completedAt;

    @Column(name = "best_rank", length = 1)
    private String bestRank;

    @Column(name = "best_combo")
    @Builder.Default
    private Integer bestCombo = 0;

    @Column(name = "best_remaining_lives")
    @Builder.Default
    private Integer bestRemainingLives = 0;

    @Column(name = "challenge_count")
    @Builder.Default
    private Integer challengeCount = 0;

    private java.time.LocalDateTime lastCompletedAt;
}
