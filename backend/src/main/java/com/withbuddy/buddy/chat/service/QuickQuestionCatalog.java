package com.withbuddy.buddy.chat.service;

import com.withbuddy.buddy.activity.entity.EventTarget;
import com.withbuddy.buddy.chat.dto.response.QuickQuestionResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class QuickQuestionCatalog {

    private final Map<EventTarget, QuickQuestionDefinition> quickQuestionsByTarget;
    private final Map<Integer, List<EventTarget>> onboardingQuickTapTargets;

    public QuickQuestionCatalog() {
        this.quickQuestionsByTarget = createQuickQuestions();
        this.onboardingQuickTapTargets = createOnboardingQuickTapTargets();
    }

    public List<QuickQuestionResponse> getRandomQuickQuestions(int limit) {
        List<QuickQuestionResponse> shuffled = new ArrayList<>(
                quickQuestionsByTarget.values().stream()
                        .map(QuickQuestionDefinition::toResponse)
                        .toList()
        );
        Collections.shuffle(shuffled);
        return shuffled.stream()
                .limit(limit)
                .toList();
    }

    public List<QuickQuestionResponse> getOnboardingQuickTaps(int dayOffset) {
        List<EventTarget> targets = onboardingQuickTapTargets.get(dayOffset);
        if (targets == null || targets.isEmpty()) {
            return List.of();
        }

        return targets.stream()
                .map(quickQuestionsByTarget::get)
                .map(QuickQuestionDefinition::toResponse)
                .toList();
    }

    public Optional<EventTarget> resolveEventTarget(String eventTarget) {
        if (eventTarget == null || eventTarget.isBlank()) {
            return Optional.empty();
        }

        try {
            EventTarget target = EventTarget.valueOf(eventTarget);
            if (!quickQuestionsByTarget.containsKey(target)) {
                return Optional.empty();
            }
            return Optional.of(target);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private Map<EventTarget, QuickQuestionDefinition> createQuickQuestions() {
        Map<EventTarget, QuickQuestionDefinition> map = new LinkedHashMap<>();

        put(map, EventTarget.QUICK_TAP_LOCATION, "🏢 출근 장소·입장 방법", "첫 출근 장소와 입장 방법이 어떻게 되나요?");
        put(map, EventTarget.QUICK_TAP_WORK_HOUR, "🕘 출근 시간", "출근 시간이 어떻게 되나요?");
        put(map, EventTarget.QUICK_TAP_DRESSCODE, "👔 복장 규정", "회사 복장 규정이 있나요?");
        put(map, EventTarget.QUICK_TAP_FIRST_DAY, "📍 첫날 누구를 찾아요?", "첫 출근 시 어디로 가야 하고 누구를 찾으면 되나요?");
        put(map, EventTarget.QUICK_TAP_ACCESS, "🔑 출입카드 받는 법", "출입카드는 어떻게 받나요?");
        put(map, EventTarget.QUICK_TAP_DOCS, "📋 제출 서류", "입사 첫날 제출해야 하는 서류는 무엇인가요?");
        put(map, EventTarget.QUICK_TAP_IT_SETUP, "💻 이메일·계정 세팅", "회사 이메일 계정은 어떻게 세팅하나요?");
        put(map, EventTarget.QUICK_TAP_EQUIPMENT, "📦 비품 신청하기", "업무에 필요한 비품은 어떻게 신청하나요?");
        put(map, EventTarget.QUICK_TAP_LEAVE_START, "📅 연차 언제부터?", "연차는 입사 후 언제부터 쓸 수 있나요?");
        put(map, EventTarget.QUICK_TAP_PRINTER, "🖨️ 프린터·사무기기 사용법", "프린터나 사무기기는 어떻게 사용하나요?");
        put(map, EventTarget.QUICK_TAP_MEETING_ROOM, "🗓️ 회의실 예약 방법", "회의실은 어떻게 예약하나요?");
        put(map, EventTarget.QUICK_TAP_MEAL, "🍱 점심 식대 지원", "점심 식대 지원은 어떻게 되나요?");
        put(map, EventTarget.QUICK_TAP_WELFARE, "💳 복지 혜택 적용 시점", "복지카드·자기계발비는 입사 후 언제부터 사용할 수 있나요?");
        put(map, EventTarget.QUICK_TAP_SLACK_GUIDE, "🧾 Slack 채널 어떻게 써요?", "사내 Slack 채널 종류와 각 채널 용도가 어떻게 되나요?");
        put(map, EventTarget.QUICK_TAP_SECURITY, "🔐 보안·파일 저장 규칙", "회사 보안 규정이나 업무 파일 저장 규칙이 어떻게 되나요?");
        put(map, EventTarget.QUICK_TAP_LATE, "🕘 지각·조퇴 처리 방법", "지각이나 조퇴가 생기면 어떻게 처리하나요?");
        put(map, EventTarget.QUICK_TAP_HALF_DAY, "📅 반차 사용 방법", "반차는 어떻게 신청하나요?");
        put(map, EventTarget.QUICK_TAP_SICK, "💊 병가 규정", "몸이 아플 때 병가는 며칠까지 쓸 수 있나요?");
        put(map, EventTarget.QUICK_TAP_APPROVAL, "📝 결재는 어떻게 해요?", "업무 결재나 승인은 어디서, 어떻게 하나요?");
        put(map, EventTarget.QUICK_TAP_SYSTEM_AUTH, "🖥️ 업무 시스템 권한 신청", "추가로 필요한 업무 시스템 권한은 어떻게 신청하나요?");
        put(map, EventTarget.QUICK_TAP_REMOTE, "🏠 재택근무 신청", "재택근무는 어떻게 신청하나요?");
        put(map, EventTarget.QUICK_TAP_WORKING_OUTSIDE, "💳 외근 보고 방법", "외근이 생기면 어떻게 보고하나요?");
        put(map, EventTarget.QUICK_TAP_CORP_CARD, "🧾 법인카드 사용 후 처리", "법인카드 사용 후 어떻게 처리하나요?");
        put(map, EventTarget.QUICK_TAP_BUDDY, "💬 버디 제도란?", "버디 제도가 어떻게 운영되나요?");
        put(map, EventTarget.QUICK_TAP_WELFARE_APPLY, "💳 복지 혜택 신청하기", "복지 혜택은 어떻게 신청하나요?");
        put(map, EventTarget.QUICK_TAP_SALARY, "💰 급여명세서 확인", "급여명세서는 어디서 확인하나요?");
        put(map, EventTarget.QUICK_TAP_PROBATION, "📊 수습 평가 기준", "수습 기간 평가는 어떤 기준으로 이루어지나요?");
        put(map, EventTarget.QUICK_TAP_LEAVE_REQ, "🏖️ 연차 신청 방법", "연차 신청은 어떻게 하나요?");
        put(map, EventTarget.QUICK_TAP_HEALTH, "🏥 건강검진 언제부터?", "건강검진 지원은 언제부터 받을 수 있나요?");
        put(map, EventTarget.QUICK_TAP_EDUCATION, "📚 교육·자기계발 지원", "업무 관련 강의나 책 구입 비용을 지원받을 수 있나요?");
        put(map, EventTarget.QUICK_TAP_CONVERT, "📋 정규직 전환 절차", "수습 기간 종료 후 정규직 전환 절차가 어떻게 되나요?");
        put(map, EventTarget.QUICK_TAP_SALARY_DEDUCTION, "📊 급여 공제 항목이 뭔가요?", "급여에서 공제되는 항목이 어떻게 되나요?");
        put(map, EventTarget.QUICK_TAP_KPI, "🎯 전환 후 평가 방식", "정규직 전환 후 목표나 평가 방식은 어떻게 되나요?");

        return Map.copyOf(map);
    }

    private Map<Integer, List<EventTarget>> createOnboardingQuickTapTargets() {
        Map<Integer, List<EventTarget>> map = new LinkedHashMap<>();

        map.put(-7, List.of(EventTarget.QUICK_TAP_LOCATION, EventTarget.QUICK_TAP_WORK_HOUR, EventTarget.QUICK_TAP_DRESSCODE));
        map.put(-3, List.of(EventTarget.QUICK_TAP_FIRST_DAY, EventTarget.QUICK_TAP_ACCESS, EventTarget.QUICK_TAP_DOCS));
        map.put(0, List.of(EventTarget.QUICK_TAP_IT_SETUP, EventTarget.QUICK_TAP_EQUIPMENT, EventTarget.QUICK_TAP_LEAVE_START));
        map.put(1, List.of(EventTarget.QUICK_TAP_PRINTER, EventTarget.QUICK_TAP_MEETING_ROOM, EventTarget.QUICK_TAP_MEAL));
        map.put(2, List.of(EventTarget.QUICK_TAP_WELFARE, EventTarget.QUICK_TAP_SLACK_GUIDE, EventTarget.QUICK_TAP_SECURITY));
        map.put(4, List.of(EventTarget.QUICK_TAP_LATE, EventTarget.QUICK_TAP_HALF_DAY, EventTarget.QUICK_TAP_SICK));
        map.put(6, List.of(EventTarget.QUICK_TAP_APPROVAL, EventTarget.QUICK_TAP_SYSTEM_AUTH, EventTarget.QUICK_TAP_REMOTE));
        map.put(9, List.of(EventTarget.QUICK_TAP_WORKING_OUTSIDE, EventTarget.QUICK_TAP_CORP_CARD, EventTarget.QUICK_TAP_BUDDY));
        map.put(13, List.of(EventTarget.QUICK_TAP_WELFARE_APPLY, EventTarget.QUICK_TAP_SALARY, EventTarget.QUICK_TAP_PROBATION));
        map.put(20, List.of(EventTarget.QUICK_TAP_LEAVE_REQ, EventTarget.QUICK_TAP_HEALTH, EventTarget.QUICK_TAP_EDUCATION));
        map.put(29, List.of(EventTarget.QUICK_TAP_CONVERT, EventTarget.QUICK_TAP_SALARY_DEDUCTION, EventTarget.QUICK_TAP_KPI));

        return Map.copyOf(map);
    }

    private void put(
            Map<EventTarget, QuickQuestionDefinition> map,
            EventTarget eventTarget,
            String buttonText,
            String content
    ) {
        map.put(eventTarget, new QuickQuestionDefinition(buttonText, content, eventTarget));
    }

    private record QuickQuestionDefinition(
            String buttonText,
            String content,
            EventTarget eventTarget
    ) {
        private QuickQuestionResponse toResponse() {
            return new QuickQuestionResponse(buttonText, content, eventTarget.name());
        }
    }
}