package com.example.spring_boot_1.LinkData;

import com.example.spring_boot_1.FolderData.Folder;
import com.example.spring_boot_1.FolderData.FolderRepository;
import com.example.spring_boot_1.RecommendationData.RecommendationWeight;
import com.example.spring_boot_1.RecommendationData.RecommendationWeightRepository;
import com.example.spring_boot_1.RecommendationData.RecommendationWeightService;
import com.example.spring_boot_1.TagData.LinkTagRepository;
import com.example.spring_boot_1.UserData.UserData;
import com.example.spring_boot_1.UserData.UserDataRepository;
import com.example.spring_boot_1.config.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
@Transactional
public class LinkDataService {

    private final LinkDataRepository linkDataRepository;
    private final FolderRepository folderRepository;
    private final UserDataRepository userDataRepository; // 매핑 처리를 위해 주입 추가
    private final RecommendationWeightService recommendationWeightService;
    private final RecommendationWeightRepository recommendationWeightRepository; // 우선도(importance) 조회
    private final LinkTagRepository linkTagRepository; // 링크 삭제 시 태그 정션 정리

    // 1. 링크 추가 (생성) — request.userName 무시, 현재 인증 사용자로 강제
    public LinkResponse create(LinkRequest request) {
        UserData userData = userDataRepository.findByUserName(SecurityUtil.currentUserName())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저 이름입니다."));

        LinkData linkData = new LinkData();
        linkData.setLink(request.getLink());
        linkData.setTitle(request.getTitle());
        linkData.setUserData(userData);

        // 폴더 위치 설정 — ERD §1.1 폴더가 PARA 의 source of truth
        Folder folder = null;
        if (request.getFolderId() != null) {
            folder = folderRepository.findById(request.getFolderId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 폴더 ID입니다."));
            requireFolderOwner(folder, userData.getId());
            linkData.setFolder(folder);
        }

        // PARA 결정 우선순위: 폴더 → 요청값 → UNSPECIFIED
        linkData.setPARAStatus(resolveParaStatus(folder, request.getPARAStatus()));

        // 컨텍스트 매칭 메타데이터 (ERD §4.2 v1) — 명시 전달되면 사용, 아니면 @PrePersist 가 추론
        if (request.getContentType() != null) {
            linkData.setContentType(ContentType.fromString(request.getContentType()));
        }
        if (request.getThumbnailUrl() != null) {
            linkData.setThumbnailUrl(request.getThumbnailUrl());
        }

        LinkData saved = linkDataRepository.save(linkData);
        // 저장과 동시에 가중치 행 생성/설정 — /today 후보 자격 즉시 확보 + 우선도 반영
        int priority = request.getPriority() == null ? 0 : clampPriority(request.getPriority());
        if (request.getPriority() != null) {
            recommendationWeightService.setImportanceForLink(saved, priorityToImportance(priority));
        } else {
            recommendationWeightService.ensureForLink(saved);
        }
        return LinkResponse.from(saved, priority);
    }

    // ── 우선도(priority 0/1/2) ↔ importance(0~1) 매핑 ──────────────
    //   문서 ERD priority. 스코어링은 importance(double) 를 쓰므로 양방향 변환.

    private static int clampPriority(int p) {
        return Math.max(0, Math.min(2, p));
    }

    static double priorityToImportance(int priority) {
        return switch (clampPriority(priority)) {
            case 2 -> 1.0;   // 매우
            case 1 -> 0.75;  // 중요
            default -> 0.5;  // 보통
        };
    }

    static int importanceToPriority(double importance) {
        if (importance >= 0.9) return 2;
        if (importance >= 0.65) return 1;
        return 0;
    }

    /** 주어진 링크들의 importance → priority 맵 (없으면 0=보통). N+1 회피용 일괄 조회. */
    private Map<Integer, Integer> priorityMapForUser(int userId) {
        return recommendationWeightRepository.findByBookmarkUserDataId(userId).stream()
                .filter(rw -> rw.getBookmark() != null)
                .collect(Collectors.toMap(
                        rw -> rw.getBookmark().getId(),
                        rw -> importanceToPriority(rw.getImportance()),
                        (a, b) -> a));
    }

    /**
     * ERD §1.1 의 의도를 살리는 PARA 결정 로직.
     * 폴더가 있으면 폴더의 paraCategory 가 항상 우선,
     * 폴더 없으면 클라이언트가 보낸 값,
     * 둘 다 없으면 UNSPECIFIED.
     */
    private ParaStatus resolveParaStatus(Folder folder, String requestedPara) {
        if (folder != null && folder.getParaCategory() != null) {
            return folder.getParaCategory();
        }
        return ParaStatus.fromString(requestedPara);
    }

    // 2. 유저별 전체 링크 조회 — userName이 현재 인증 사용자와 일치해야 함
    @Transactional(readOnly = true)
    public List<LinkResponse> getByUserName(String userName) {
        SecurityUtil.requireOwnerByName(userName);
        List<LinkData> links = linkDataRepository.findByUserDataUserName(userName);
        Map<Integer, Integer> prio = priorityMapForUser(SecurityUtil.currentUserId());
        return links.stream()
                .map(l -> LinkResponse.from(l, prio.getOrDefault(l.getId(), 0)))
                .toList();
    }

    /**
     * 본인 데이터에 한정해 title/url 부분일치 검색. 최대 50개.
     */
    @Transactional(readOnly = true)
    public List<LinkResponse> search(String q) {
        if (q == null || q.isBlank()) return List.of();
        int userId = SecurityUtil.currentUserId();
        return linkDataRepository.searchForUser(userId, q.strip()).stream()
                .limit(50)
                .map(LinkResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LinkResponse> getByFolderId(int folderId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 폴더 ID입니다."));
        requireFolderOwner(folder, SecurityUtil.currentUserId());
        List<LinkData> links = linkDataRepository.findByFolderId(folderId);
        Map<Integer, Integer> prio = priorityMapForUser(SecurityUtil.currentUserId());
        return links.stream()
                .map(l -> LinkResponse.from(l, prio.getOrDefault(l.getId(), 0)))
                .toList();
    }

    // 3. 읽음 처리 - 리마인드 점수의 unread/recency 신호에 반영됨
    public void markRead(int id) {
        LinkData linkData = linkDataRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 링크 id입니다."));
        requireLinkOwner(linkData);
        if (linkData.isRead()) {
            return;
        }
        linkData.setRead(true);
        linkData.setReadAt(LocalDateTime.now());
        linkDataRepository.save(linkData);
        // weightValue 캐시 폐기 (2026-05-21) — scoreRemind 가 link.isRead() 를 live 로 읽음
    }

    // 4. 링크 관리 (정보 수정 및 폴더 이동) — 다른 사용자 명의로 이전 금지
    public void update(int id, LinkRequest request) {
        LinkData linkData = linkDataRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 링크 id입니다."));
        requireLinkOwner(linkData);

        // userName 변경 시도는 무시 — 자기 자신만 소유 가능
        UserData userData = linkData.getUserData();

        linkData.setLink(request.getLink());
        linkData.setTitle(request.getTitle());

        // 폴더 구조 변경 및 이동 연산
        Folder newFolder = null;
        if (request.getFolderId() != null) {
            newFolder = folderRepository.findById(request.getFolderId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 폴더 ID입니다."));
            requireFolderOwner(newFolder, userData.getId());
            linkData.setFolder(newFolder);
        } else {
            linkData.setFolder(null);
        }

        // 폴더가 PARA 의 source of truth — 이동 후 폴더의 PARA 따라감 (ERD §1.1)
        linkData.setPARAStatus(resolveParaStatus(newFolder, request.getPARAStatus()));

        // 컨텍스트 매칭 메타 — 보낸 필드만 갱신, host 는 link 변경에 따라 자동 추출됨
        if (request.getContentType() != null) {
            linkData.setContentType(ContentType.fromString(request.getContentType()));
        }
        if (request.getThumbnailUrl() != null) {
            linkData.setThumbnailUrl(request.getThumbnailUrl());
        }

        linkDataRepository.save(linkData);
        // 추천은 매 호출 live 계산이라 dirty 플래그 불필요 (2026-05-21)
    }

    // 5. 링크 삭제
    public void delete(int id) {
        LinkData linkData = linkDataRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 링크 id입니다."));
        requireLinkOwner(linkData);
        recommendationWeightService.deleteByBookmarkId(id);
        linkTagRepository.deleteByLinkId(id); // 태그 정션 먼저 정리 (FK 무결성)
        linkDataRepository.delete(linkData);
    }

    /**
     * 일괄 작업 — 본인 소유 + 존재하는 ID 만 처리하고 나머지는 조용히 건너뛴다.
     * 보안상 클라이언트가 보낸 ID 는 신뢰하지 않고 소유권으로 필터링한다.
     */
    public LinkBulkResponse bulk(LinkBulkRequest request) {
        if (request == null || request.ids() == null || request.ids().isEmpty()) {
            throw new IllegalArgumentException("ids 가 비어 있습니다.");
        }
        String action = request.action() == null ? "" : request.action().trim().toUpperCase(Locale.ROOT);
        int userId = SecurityUtil.currentUserId();
        int requested = request.ids().size();

        // 본인 소유 + 존재하는 링크만 추림
        List<LinkData> owned = linkDataRepository.findAllById(request.ids()).stream()
                .filter(l -> l.getUserData() != null && l.getUserData().getId() == userId)
                .toList();

        int affected = switch (action) {
            case "DELETE" -> bulkDelete(owned);
            case "MOVE" -> bulkMove(owned, request.folderId(), userId);
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 action 입니다: " + request.action() + " (DELETE | MOVE)");
        };
        return new LinkBulkResponse(requested, affected);
    }

    private int bulkDelete(List<LinkData> links) {
        for (LinkData link : links) {
            recommendationWeightService.deleteByBookmarkId(link.getId());
            linkTagRepository.deleteByLinkId(link.getId()); // 태그 정션 정리 (FK 무결성)
        }
        linkDataRepository.deleteAll(links);
        return links.size();
    }

    private int bulkMove(List<LinkData> links, Integer folderId, int userId) {
        if (folderId == null) {
            throw new IllegalArgumentException("MOVE 는 folderId 가 필수입니다.");
        }
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 폴더 ID입니다."));
        requireFolderOwner(folder, userId);
        for (LinkData link : links) {
            link.setFolder(folder);
            // 폴더가 PARA 의 source of truth (ERD §1.1) — 이동 후 폴더 PARA 따라감
            link.setPARAStatus(resolveParaStatus(folder, null));
        }
        linkDataRepository.saveAll(links);
        return links.size();
    }

    private void requireLinkOwner(LinkData linkData) {
        if (linkData.getUserData() == null
                || linkData.getUserData().getId() != SecurityUtil.currentUserId()) {
            throw new AccessDeniedException("해당 링크의 소유자가 아닙니다.");
        }
    }

    private void requireFolderOwner(Folder folder, int ownerId) {
        if (folder.getUserData() == null || folder.getUserData().getId() != ownerId) {
            throw new AccessDeniedException("해당 폴더의 소유자가 아닙니다.");
        }
    }

}
