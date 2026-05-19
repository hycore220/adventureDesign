package com.example.spring_boot_1.FolderData;

import com.example.spring_boot_1.LinkData.LinkResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class FolderContentsResponse {
    private List<FolderResponse> subFolders; 
    private List<LinkResponse> links;        
}