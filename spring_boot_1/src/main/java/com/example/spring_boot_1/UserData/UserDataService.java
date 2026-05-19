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

    public UserData create(String userName, String password) {
        UserData userData = new UserData();
        userData.setUserName(userName);
        userData.setPassword(passwordEncoder.encode(password));
        this.userDataRepository.save(userData);

        String[] paraNames = {"Project", "Area", "Resources", "Archive"};
        
        for (String name : paraNames) {
            Folder paraFolder = new Folder();
            paraFolder.setName(name);
            paraFolder.setUserData(userData);       // 새로 생성된 유저 객체 연결
            paraFolder.setParentFolder(null);       // 최상위 폴더이므로 null
            
            folderRepository.save(paraFolder);
        }

        return userData;
    }
}
