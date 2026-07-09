package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 50)
    private String username;

    @Column(unique = true, length = 100)
    private String email;

    @Column(length = 255)
    private String password;

    private Integer level = 1;
    private Integer exp = 0;
    private Integer coins = 0;
    private Integer bossPoints = 0;

    @Column(length = 50)
    private String title;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
