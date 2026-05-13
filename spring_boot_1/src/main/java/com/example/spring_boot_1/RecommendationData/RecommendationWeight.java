package com.example.spring_boot_1.RecommendationData;

import com.example.spring_boot_1.LinkData.LinkData;
import com.example.spring_boot_1.UserData.UserData;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(
        name = "recommendation_weight",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_recommendation_weight_user_bookmark",
                        columnNames = {"user_data_id", "link_data_id"}
                )
        },
        indexes = {
                @Index(name = "idx_recommendation_weight_user", columnList = "user_data_id"),
                @Index(name = "idx_recommendation_weight_bookmark", columnList = "link_data_id")
        }
)
public class RecommendationWeight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private double weightValue;

    @ManyToOne
    @JoinColumn(name = "user_data_id")
    @JsonIgnoreProperties({"password"})
    private UserData user;

    @Column
    private boolean snooze;

    @Column
    private int frequency;

    @Column
    private LocalDateTime lastUpdate;

    @Column
    private double importance;

    @Column
    private double similarity;

    @Column(columnDefinition = "TEXT")
    private String embeddingText;

    @Column(columnDefinition = "LONGTEXT")
    private String embeddingVector;

    @Column
    private String embeddingModel;

    @Column
    private LocalDateTime embeddingUpdatedAt;

    @ManyToOne
    @JoinColumn(name = "link_data_id")
    private LinkData bookmark;
}
