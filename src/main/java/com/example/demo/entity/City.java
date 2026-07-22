package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "cities")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class City {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "city_name", length = 50)
    private String name;

    @Column(length = 10)
    private String code;

    @Column(name = "description", length = 500)
    private String intro;

    @Column(length = 2000)
    private String story;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Column(name = "city_order")
    @Builder.Default
    private Integer unlockOrder = 0;

    private String bossName;
    @Builder.Default
    private Integer bossPower = 0;

    @Column(name = "boss_question", length = 500)
    private String bossQuestion;

    @Column(name = "boss_option_a", length = 255)
    private String bossOptionA;

    @Column(name = "boss_option_b", length = 255)
    private String bossOptionB;

    @Column(name = "boss_option_c", length = 255)
    private String bossOptionC;

    @Column(name = "boss_option_d", length = 255)
    private String bossOptionD;

    @JsonIgnore
    @Column(name = "boss_correct_answer", length = 1)
    private String bossCorrectAnswer;

    @Column(name = "badge_icon", length = 255)
    private String badgeIcon;

    @Column(name = "badge_name", length = 100)
    private String badgeName;
}
