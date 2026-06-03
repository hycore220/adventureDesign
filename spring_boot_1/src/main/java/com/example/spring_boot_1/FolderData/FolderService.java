package com.example.spring_boot_1.FolderData;

import com.example.spring_boot_1.LinkData.LinkData;
import com.example.spring_boot_1.LinkData.LinkDataRepository;
import com.example.spring_boot_1.LinkData.LinkDataService;
import com.example.spring_boot_1.LinkData.LinkResponse; // 임포트 추가
import com.example.spring_boot_1.LinkData.ParaStatus;
import com.example.spring_boot_1.UserData.UserData;
import com.example.spring_boot_1.UserData.UserDataRepository;
import com.example.spring_boot_1.config.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
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
    private final LinkDataService linkDataService;
    private final UserDataRepository userDataRepository;

    // 1. 특정 유저의 최상위 폴더 조회
    @Transactional(readOnly = true)
    public List<FolderResponse> getRootFolders(String userName) {
        SecurityUtil.requireOwnerByName(userName);
        List<Folder> rootFolders = folderRepository.findByUserDataUserNameAndParentFolderIsNull(userName);

        return rootFolders.stream()
                .map(FolderResponse::from)
                .collect(Collectors.toList());
    }

    // 2. 특정 폴더 내부의 하위 폴더 + 링크 동시 조회
    @Transactional(readOnly = true)
    public FolderContentsResponse getFolderContents(int folderId) {
        Folder owner = folderRepository.findById(folderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 폴더 id입니다."));
        requireFolderOwner(owner);

        List<Folder> subFolders = folderRepository.findByParentFolderId(folderId);
        List<FolderResponse> subFolderResponses = subFolders.stream()
                .map(FolderResponse::from)
                .collect(Collectors.toList());

        List<LinkData> links = linkDataRepository.findByFolderId(folderId);
        List<LinkResponse> linkResponses = links.stream()
                .map(LinkResponse::from)
                .collect(Collectors.toList());

        return new FolderContentsResponse(subFolderResponses, linkResponses);
    }

    // 3. 폴더 삭제 (PARA 방어막 유지)
    public void deleteFolder(int folderId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 폴더 id입니다."));
        requireFolderOwner(folder);

        if (folder.getParentFolder() == null) {
            throw new IllegalArgumentException("최상위 PARA 시스템 폴더는 삭제할 수 없습니다.");
        }

        // 하위 링크 먼저 정리 — link_data.folder_id → folder.id FK 무결성.
        // LinkDataService.delete 가 가중치/태그 정션까지 함께 정리한다.
        List<LinkData> children = linkDataRepository.findByFolderId(folderId);
        for (LinkData link : children) {
            linkDataService.delete(link.getId());
        }

        folderRepository.delete(folder);
    }

    // 4. 폴더 생성 — request.userName 무시, 현재 인증 사용자로 강제
    public void createFolder(FolderRequest request) {
        UserData userData = userDataRepository.findByUserName(SecurityUtil.currentUserName())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저 이름입니다."));

        if (request.getParentId() == null) {
            throw new IllegalArgumentException("최상위 폴더는 고정되어 있습니다. 하위 폴더의 부모 ID를 지정해주세요.");
        }

        Folder parent = folderRepository.findById(request.getParentId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 부모 폴더 ID입니다."));
        requireFolderOwner(parent);

        Folder folder = new Folder();
        folder.setName(request.getName());
        folder.setUserData(userData);
        folder.setParentFolder(parent);
        // 하위 폴더는 부모의 PARA 를 상속한다 (ERD §4.1)
        folder.setParaCategory(parent.getParaCategory());

        folderRepository.save(folder);
    }

    /**
     * 폴더 정보 갱신 — name / paraCategory.
     *
     * ERD §1.1: 폴더가 PARA 의 source of truth.
     * paraCategory 가 변경되면 소속 링크들의 PARAStatus 캐시도 cascade 동기화한다.
     * 변경된 사용자의 추천 dirty 플래그도 켜서 재계산을 트리거.
     */
    public FolderResponse updateFolder(int folderId, FolderUpdateRequest request) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 폴더 id입니다."));
        requireFolderOwner(folder);

        if (request.name() != null && !request.name().isBlank()) {
            folder.setName(request.name());
        }

        boolean paraChanged = false;
        if (request.paraCategory() != null) {
            ParaStatus newPara = ParaStatus.fromString(request.paraCategory());
            if (!java.util.Objects.equals(folder.getParaCategory(), newPara)) {
                folder.setParaCategory(newPara);
                paraChanged = true;
            }
        }

        folderRepository.save(folder);

        if (paraChanged) {
            cascadeParaToLinks(folder);
        }

        return FolderResponse.from(folder);
    }

    /** 폴더 PARA 변경 시 소속 링크들의 PARAStatus 캐시를 모두 새 값으로 갱신. */
    private void cascadeParaToLinks(Folder folder) {
        List<LinkData> links = linkDataRepository.findByFolderId(folder.getId());
        for (LinkData link : links) {
            link.setPARAStatus(folder.getParaCategory());
        }
        linkDataRepository.saveAll(links);
    }

    private void requireFolderOwner(Folder folder) {
        if (folder.getUserData() == null
                || folder.getUserData().getId() != SecurityUtil.currentUserId()) {
            throw new AccessDeniedException("해당 폴더의 소유자가 아닙니다.");
        }
    }
}