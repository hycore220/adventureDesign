package com.example.spring_boot_1.FolderData;

import com.example.spring_boot_1.LinkData.LinkData;
import com.example.spring_boot_1.LinkData.LinkDataRepository;
import com.example.spring_boot_1.LinkData.LinkResponse; // 임포트 추가
import com.example.spring_boot_1.UserData.UserData;
import com.example.spring_boot_1.UserData.UserDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
@Transactional
public class FolderService {

    private final FolderRepository folderRepository;
    private final LinkDataRepository linkDataRepository;
    private final UserDataRepository userDataRepository;

    // 1. 특정 유저의 최상위 폴더 조회 (FolderResponse DTO로 변환하여 반환)
    @Transactional(readOnly = true)
    public List<FolderResponse> getRootFolders(String userName) {
        List<Folder> rootFolders = folderRepository.findByUserDataUserNameAndParentFolderIsNull(userName);
        
        return rootFolders.stream()
                .map(folder -> new FolderResponse(
                        folder.getId(),
                        folder.getName(),
                        null
                ))
                .collect(Collectors.toList());
    }

    // 2. 특정 폴더 내부의 하위 폴더 + 링크 동시 조회 (각각 DTO로 변환)
    @Transactional(readOnly = true)
    public FolderContentsResponse getFolderContents(int folderId) {
        // 하위 폴더 조회 및 DTO 변환
        List<Folder> subFolders = folderRepository.findByParentFolderId(folderId);
        List<FolderResponse> subFolderResponses = subFolders.stream()
                .map(folder -> new FolderResponse(
                        folder.getId(),
                        folder.getName(),
                        folder.getParentFolder() != null ? folder.getParentFolder().getId() : null
                ))
                .collect(Collectors.toList());

        // 하위 링크 조회 및 DTO 변환
        List<LinkData> links = linkDataRepository.findByFolderId(folderId);
        List<LinkResponse> linkResponses = links.stream()
                .map(link -> new LinkResponse(
                        link.getId(),
                        link.getLink(),
                        link.getTitle(),
                        link.getPARAStatus(),
                        link.getLastUpdate()
                ))
                .collect(Collectors.toList());
        
        return new FolderContentsResponse(subFolderResponses, linkResponses);
    }

    // 3. 폴더 삭제 (PARA 방어막 유지)
    public void deleteFolder(int folderId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 폴더 id입니다."));
        
        if (folder.getParentFolder() == null) {
            throw new IllegalArgumentException("최상위 PARA 시스템 폴더는 삭제할 수 없습니다.");
        }

        folderRepository.delete(folder);
    }

    // 4. 폴더 생성 (부모 필수 검증 유지)
    public void createFolder(FolderRequest request) {
        UserData userData = userDataRepository.findByUserName(request.getUserName())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저 이름입니다."));

        if (request.getParentId() == null) {
            throw new IllegalArgumentException("최상위 폴더는 고정되어 있습니다. 하위 폴더의 부모 ID를 지정해주세요.");
        }

        Folder parent = folderRepository.findById(request.getParentId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 부모 폴더 ID입니다."));

        Folder folder = new Folder();
        folder.setName(request.getName());
        folder.setUserData(userData);
        folder.setParentFolder(parent);

        folderRepository.save(folder);
    }
}