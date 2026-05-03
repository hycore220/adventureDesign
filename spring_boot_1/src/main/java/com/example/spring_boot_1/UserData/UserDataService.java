package com.example.spring_boot_1.UserData;

import org.springframework.security.crypto.password.PasswordEncoder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;


@RequiredArgsConstructor
@Service
public class UserDataService {

    private  final UserDataRepository userDataRepository;
    private  final PasswordEncoder passwordEncoder;

    public UserData create(String userName, String password) {
        UserData userData = new UserData();
        userData.setUserName(userName);
        userData.setPassword(passwordEncoder.encode(password));
        this.userDataRepository.save(userData);
        return userData;

    }
}
