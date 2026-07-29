package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "check_ins",
        uniqueConstraints = @UniqueConstraint(
                name = "idx_checkins_user_landmark",
                columnNames = {"user_id", "landmark_id"}
        ),
        indexes = @Index(
                name = "idx_checkins_user_completed",
                columnList = "user_id, completed"
        )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Checkin {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "landmark_id")
    private Scene scene;

    @Column(name = "selected_answer", length = 1)
    private String selectedAnswer;

    @Column(name = "quiz_correct")
    @Builder.Default
    private Boolean quizCorrect = false;

    @Builder.Default
    private Boolean completed = false;

    @Builder.Default
    private Integer earnedExp = 0;
    @Builder.Default
    private Integer earnedCoins = 0;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    private LocalDateTime checkinTime;
}
