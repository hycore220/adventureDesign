package com.example.spring_boot_1.UserData;

import com.example.spring_boot_1.FolderData.Folder;          // Folder 엔티티 임포트
import com.example.spring_boot_1.FolderData.FolderRepository; // FolderRepository 임포트
import org.springframework.security.crypto.password.PasswordEncoder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Transactional // 데이터 일관성을 위해 트랜잭션 추가
public class UserDataService {

    private final UserDataRepository userDataRepository;
    private final PasswordEncoder passwordEncoder;
    private final FolderRepository folderRepository; // 폴더 생성을 위해 주입 추가
    private final UserReminderPrefsService userReminderPrefsService; // 가입 시 기본 prefs 생성

    public UserData create(String userName, String password) {
        if (userDataRepository.findByUserName(userName).isPresent()) {
            throw new IllegalArgumentException("이미 가입된 사용자입니다.");
        }

        UserData userData = new UserData();
        userData.setUserName(userName);
        userData.setPassword(passwordEncoder.encode(password));
        this.userDataRepository.save(userData);

        // PARA 기본 폴더 4종 자동 생성 (PRD §4 + ERD §4.1)
        // 폴더의 paraCategory 가 source of truth — 링크 PARA 는 이걸 따라간다.
        Object[][] paraDefaults = {
                {"Project", com.example.spring_boot_1.LinkData.ParaStatus.PROJECT},
                {"Area", com.example.spring_boot_1.LinkData.ParaStatus.AREA},
                {"Resources", com.example.spring_boot_1.LinkData.ParaStatus.RESOURCE},
                {"Archive", com.example.spring_boot_1.LinkData.ParaStatus.ARCHIVE}
        };
        for (Object[] entry : paraDefaults) {
            Folder paraFolder = new Folder();
            paraFolder.setName((String) entry[0]);
            paraFolder.setParaCategory((com.example.spring_boot_1.LinkData.ParaStatus) entry[1]);
            paraFolder.setUserData(userData);
            paraFolder.setParentFolder(null);
            folderRepository.save(paraFolder);
        }

        // 리마인드 알림 기본값 row (REMIND_STRATEGY §6.2)
        userReminderPrefsService.createDefault(userData.getId());

        return userData;
    }

    @Transactional(readOnly = true)
    public UserData authenticate(String userName, String password) {
        UserData userData = userDataRepository.findByUserName(userName)
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 사용자입니다."));

        if (!passwordEncoder.matches(password, userData.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        return userData;
    }

    @Transactional(readOnly = true)
    public UserData getByUserName(String userName) {
        return userDataRepository.findByUserName(userName)
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 사용자입니다."));
    }
}
