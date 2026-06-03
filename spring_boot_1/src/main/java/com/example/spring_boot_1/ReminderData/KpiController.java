package com.example.spring_boot_1.ReminderData;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * /metrics/me — 인증된 사용자 본인의 리마인드 KPI.
 *
 * from / to 가 누락되면 최근 30일.
 */
@RestController
@RequestMapping("/metrics/me")
@RequiredArgsConstructor
public class KpiController {

    private final KpiService kpiService;

    @GetMapping("/reminders")
    public ResponseEntity<KpiResponses.CtrSummary> ctrSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        Range r = resolveRange(from, to);
        return ResponseEntity.ok(kpiService.ctrSummary(r.from(), r.to()));
    }

    @GetMapping("/seven-day-click")
    public ResponseEntity<KpiResponses.SevenDayClickRate> sevenDayClick(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        Range r = resolveRange(from, to);
        return ResponseEntity.ok(kpiService.sevenDayClickRate(r.from(), r.to()));
    }

    @GetMapping("/completion")
    public ResponseEntity<KpiResponses.CompletionRate> completion(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        Range r = resolveRange(from, to);
        return ResponseEntity.ok(kpiService.completionRate(r.from(), r.to()));
    }

    private Range resolveRange(LocalDateTime from, LocalDateTime to) {
        LocalDateTime resolvedTo = to != null ? to : LocalDateTime.now();
        LocalDateTime resolvedFrom = from != null ? from : resolvedTo.minusDays(30);
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new IllegalArgumentException("from은 to 보다 과거여야 합니다.");
        }
        return new Range(resolvedFrom, resolvedTo);
    }

    private record Range(LocalDateTime from, LocalDateTime to) {}
}
