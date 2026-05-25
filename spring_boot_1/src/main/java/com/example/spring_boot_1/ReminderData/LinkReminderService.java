package com.example.spring_boot_1.ReminderData;

import com.example.spring_boot_1.LinkData.LinkData;
import com.example.spring_boot_1.LinkData.LinkDataRepository;
import com.example.spring_boot_1.config.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@Service
@Transactional
public class LinkReminderService {

    private static final Set<String> CHANNELS = Set.of("dashboard", "extension", "email", "push");
    private static final Set<String> MODES = Set.of(
            "daily",
            "weekly",
            "resurface",
            "priority",
            "youtube_ctx",
            "domain_ctx"
    );

    private final LinkReminderRepository linkReminderRepository;
    private final LinkDataRepository linkDataRepository;

    public LinkReminderResponse create(LinkReminderRequest request) {
        if (request.getLinkId() == null) {
            throw new IllegalArgumentException("링크 ID가 필요합니다.");
        }

        LinkData linkData = linkDataRepository.findById(request.getLinkId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 링크 id입니다."));
        // 인증된 사용자만 자기 링크에 리마인드 발송 가능
        if (linkData.getUserData() == null
                || linkData.getUserData().getId() != SecurityUtil.currentUserId()) {
            throw new AccessDeniedException("해당 링크의 소유자가 아닙니다.");
        }

        LinkReminder reminder = new LinkReminder();
        reminder.setLinkData(linkData);
        reminder.setUserData(linkData.getUserData());
        reminder.setChannel(normalizeChannel(request.getChannel()));
        reminder.setMode(normalizeMode(request.getMode()));
        reminder.setSnoozedUntil(request.getSnoozedUntil());

        return LinkReminderResponse.from(linkReminderRepository.save(reminder));
    }

    @Transactional(readOnly = true)
    public List<LinkReminderResponse> getByUserName(String userName) {
        SecurityUtil.requireOwnerByName(userName);
        return linkReminderRepository.findByUserDataUserNameOrderBySentAtDesc(userName)
                .stream()
                .map(LinkReminderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LinkReminderResponse> getByLinkId(int linkId) {
        LinkData link = linkDataRepository.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 링크 id입니다."));
        if (link.getUserData() == null
                || link.getUserData().getId() != SecurityUtil.currentUserId()) {
            throw new AccessDeniedException("해당 링크의 소유자가 아닙니다.");
        }
        return linkReminderRepository.findByLinkDataIdOrderBySentAtDesc(linkId)
                .stream()
                .map(LinkReminderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LinkReminderResponse> getByUserNameAndLinkId(String userName, int linkId) {
        SecurityUtil.requireOwnerByName(userName);
        return linkReminderRepository.findByUserDataUserNameAndLinkDataIdOrderBySentAtDesc(userName, linkId)
                .stream()
                .map(LinkReminderResponse::from)
                .toList();
    }

    public LinkReminderResponse markOpened(int id) {
        LinkReminder reminder = getReminder(id);
        requireReminderOwner(reminder);
        reminder.setOpenedAt(LocalDateTime.now());
        return LinkReminderResponse.from(linkReminderRepository.save(reminder));
    }

    public LinkReminderResponse snooze(int id, LocalDateTime snoozedUntil) {
        if (snoozedUntil == null) {
            throw new IllegalArgumentException("스누즈 시간이 필요합니다.");
        }

        LinkReminder reminder = getReminder(id);
        requireReminderOwner(reminder);
        reminder.setSnoozedUntil(snoozedUntil);
        return LinkReminderResponse.from(linkReminderRepository.save(reminder));
    }

    public void delete(int id) {
        LinkReminder reminder = getReminder(id);
        requireReminderOwner(reminder);
        linkReminderRepository.delete(reminder);
    }

    private LinkReminder getReminder(int id) {
        return linkReminderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 리마인더 id입니다."));
    }

    private void requireReminderOwner(LinkReminder reminder) {
        if (reminder.getUserData() == null
                || reminder.getUserData().getId() != SecurityUtil.currentUserId()) {
            throw new AccessDeniedException("해당 리마인더의 소유자가 아닙니다.");
        }
    }

    private String normalizeChannel(String channel) {
        String value = normalize(channel, "dashboard");
        if (!CHANNELS.contains(value)) {
            throw new IllegalArgumentException("지원하지 않는 리마인더 채널입니다.");
        }
        return value;
    }

    private String normalizeMode(String mode) {
        String value = normalize(mode, "daily");
        if (!MODES.contains(value)) {
            throw new IllegalArgumentException("지원하지 않는 리마인더 모드입니다.");
        }
        return value;
    }

    private String normalize(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim().toLowerCase();
    }
}
