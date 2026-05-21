package com.example.spring_boot_1.UserData;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /me/reminder-prefs — 인증된 사용자의 리마인드 설정.
 *
 * 다른 사용자 id 로 조작 못 하도록 path 에 userId 안 받음.
 */
@RestController
@RequestMapping("/me/reminder-prefs")
@RequiredArgsConstructor
public class UserReminderPrefsController {

    private final UserReminderPrefsService service;

    @GetMapping
    public ResponseEntity<UserReminderPrefs> getMine() {
        return ResponseEntity.ok(service.getMine());
    }

    @PutMapping
    public ResponseEntity<UserReminderPrefs> updateMine(@RequestBody UserReminderPrefsRequest request) {
        return ResponseEntity.ok(service.updateMine(request));
    }
}
