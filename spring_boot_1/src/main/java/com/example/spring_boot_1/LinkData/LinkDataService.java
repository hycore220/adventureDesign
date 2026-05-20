package com.example.spring_boot_1.LinkData;

import com.example.spring_boot_1.FolderData.Folder;
import com.example.spring_boot_1.FolderData.FolderRepository;
import com.example.spring_boot_1.UserData.UserData;
import com.example.spring_boot_1.UserData.UserDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
@Transactional
public class LinkDataService {

    private final LinkDataRepository linkDataRepository;
    private final FolderRepository folderRepository;
    private final UserDataRepository userDataRepository; // 매핑 처리를 위해 주입 추가

    // 1. 링크 추가 (생성)
    public void create(LinkRequest request) {
        UserData userData = userDataRepository.findByUserName(request.getUserName())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저 이름입니다."));

        LinkData linkData = new LinkData();
        linkData.setLink(request.getLink());
        linkData.setTitle(request.getTitle());
        linkData.setUserData(userData); // 조회한 유저 엔티티 주입
        linkData.setPARAStatus(request.getPARAStatus());

        // 폴더 위치 설정
        if (request.getFolderId() != null) {
            Folder folder = folderRepository.findById(request.getFolderId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 폴더 ID입니다."));
            linkData.setFolder(folder);
        }

        linkDataRepository.save(linkData);
    }

    // 2. 유저별 전체 링크 조회
    @Transactional(readOnly = true)
    public List<LinkResponse> getByUserName(String userName) {
        List<LinkData> links = linkDataRepository.findByUserDataUserName(userName);
        return links.stream()
                .map(link -> new LinkResponse(
                        link.getId(), link.getLink(), link.getTitle(), 
                        link.getPARAStatus(), link.getLastUpdate()))
                .toList(); 
    }

    @Transactional(readOnly = true)
    public List<LinkResponse> getByFolderId(int folderId) {
        List<LinkData> links = linkDataRepository.findByFolderId(folderId);
        return links.stream()
                .map(link -> new LinkResponse(
                        link.getId(), link.getLink(), link.getTitle(), 
                        link.getPARAStatus(), link.getLastUpdate()))
                .toList();
    }

    // 4. 링크 관리 (정보 수정 및 폴더 이동)
    public void update(int id, LinkRequest request) {
        LinkData linkData = linkDataRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 링크 id입니다."));

        UserData userData = userDataRepository.findByUserName(request.getUserName())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저 이름입니다."));

        linkData.setLink(request.getLink());
        linkData.setTitle(request.getTitle());
        linkData.setUserData(userData);
        linkData.setPARAStatus(request.getPARAStatus());

        // 폴더 구조 변경 및 이동 연산
        if (request.getFolderId() != null) {
            Folder newFolder = folderRepository.findById(request.getFolderId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 폴더 ID입니다."));
            linkData.setFolder(newFolder);
        } else {
            linkData.setFolder(null); // folderId가 넘어오지 않으면 최상위 위치로 꺼냄
        }

        linkDataRepository.save(linkData);
    }

    // 5. 링크 삭제
    public void delete(int id) {
        if (!linkDataRepository.existsById(id)) {
            throw new IllegalArgumentException("존재하지 않는 링크 id입니다.");
        }
        linkDataRepository.deleteById(id);
    }
}