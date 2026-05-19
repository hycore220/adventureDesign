package com.example.spring_boot_1.LinkData;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LinkRequest {
    private String link;
    private String title;
    private String userName;   
    private String PARAStatus;
    private Integer folderId;  
}