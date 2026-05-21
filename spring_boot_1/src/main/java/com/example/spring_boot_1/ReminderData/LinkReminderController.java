package com.example.spring_boot_1.ReminderData;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/link-reminders")
public class LinkReminderController {

    private final LinkReminderService linkReminderService;

    @PostMapping
    public ResponseEntity<LinkReminderResponse> create(@RequestBody LinkReminderRequest request) {
        return ResponseEntity.ok(linkReminderService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<LinkReminderResponse>> getAll(
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) Integer linkId
    ) {
        if (userName != null && linkId != null) {
            return ResponseEntity.ok(linkReminderService.getByUserNameAndLinkId(userName, linkId));
        }
        if (userName != null) {
            return ResponseEntity.ok(linkReminderService.getByUserName(userName));
        }
        if (linkId != null) {
            return ResponseEntity.ok(linkReminderService.getByLinkId(linkId));
        }
        throw new IllegalArgumentException("userName 또는 linkId가 필요합니다.");
    }

    @PostMapping("/{id}/open")
    public ResponseEntity<LinkReminderResponse> markOpened(@PathVariable int id) {
        return ResponseEntity.ok(linkReminderService.markOpened(id));
    }

    @PostMapping("/{id}/snooze")
    public ResponseEntity<LinkReminderResponse> snooze(
            @PathVariable int id,
            @RequestBody SnoozeReminderRequest request
    ) {
        return ResponseEntity.ok(linkReminderService.snooze(id, request.getSnoozedUntil()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable int id) {
        linkReminderService.delete(id);
        return ResponseEntity.ok("리마인더 삭제 완료");
    }
}
