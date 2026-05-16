package com.example.spring_boot_1.LinkData;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "link_data")
public class LinkData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // 자동 증가!
    private int id;

    @Column(columnDefinition = "TEXT")
    private String link;

    @Column
    private String userName;

    @Column
    private String PARAStatus;

    @Column
    private LocalDateTime lastUpdate;

    @PrePersist
    @PreUpdate
    public void updateTime() {
        this.lastUpdate = LocalDateTime.now();
    }
}