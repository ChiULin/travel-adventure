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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private City city;

    private Boolean unlocked = false;
    private Boolean bossUnlocked = false;
    private Boolean bossCompleted = false;
    private Boolean badgeUnlocked = false;

    private java.time.LocalDateTime completedAt;

    @Column(name = "best_rank", length = 1)
    private String bestRank;

    @Column(name = "best_combo")
    private Integer bestCombo = 0;

    @Column(name = "best_remaining_lives")
    private Integer bestRemainingLives = 0;

    @Column(name = "challenge_count")
    private Integer challengeCount = 0;

    private java.time.LocalDateTime lastCompletedAt;
}
