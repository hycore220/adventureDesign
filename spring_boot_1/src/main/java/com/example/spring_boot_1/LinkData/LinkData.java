package com.example.spring_boot_1.LinkData;

import com.example.spring_boot_1.UserData.UserData;
import com.example.spring_boot_1.FolderData.Folder;
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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(columnDefinition = "TEXT")
    private String link;

    @Column
    private String title;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserData userData;

    @Column
    private String PARAStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private Folder folder;

    @Column
    private LocalDateTime lastUpdate;

    @PrePersist
    @PreUpdate
    public void updateTime() {
        this.lastUpdate = LocalDateTime.now();
    }
}