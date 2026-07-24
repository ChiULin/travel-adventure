package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "landmarks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Scene {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "landmark_name", length = 100)
    private String name;

    @Column(length = 50)
    private String type;

    @Column(length = 255)
    private String description;

    @Column(length = 2000)
    private String story;

    @Column(length = 255)
    private String imageUrl;

    @Column(name = "quiz_question", length = 500)
    private String quizQuestion;

    @Column(name = "quiz_option_a", length = 255)
    private String quizOptionA;

    @Column(name = "quiz_option_b", length = 255)
    private String quizOptionB;

    @Column(name = "quiz_option_c", length = 255)
    private String quizOptionC;

    @Column(name = "quiz_option_d", length = 255)
    private String quizOptionD;

    @JsonIgnore
    @Column(name = "quiz_correct_answer", length = 1)
    private String quizCorrectAnswer;

    @Column(name = "quiz_explanation", length = 500)
    private String quizExplanation;

    @Builder.Default
    private Integer rarity = 1;

    @Builder.Default
    private Integer expReward = 0;
    @Builder.Default
    private Integer coinReward = 0;

    @Builder.Default
    private Boolean isHidden = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private City city;
}
