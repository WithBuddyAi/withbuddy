package com.withbuddy.buddy.chat.service;

import com.withbuddy.buddy.chat.dto.response.QuickQuestionResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuickQuestionCatalogTest {

    @Test
    void returnsPreQuickQuestionsInFixedOrder() {
        QuickQuestionCatalog catalog = new QuickQuestionCatalog();

        assertThat(catalog.getPreQuickQuestions())
                .extracting(
                        QuickQuestionResponse::getButtonText,
                        QuickQuestionResponse::getContent,
                        QuickQuestionResponse::getEventTarget
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "📋 제출 서류",
                                "입사 첫날 제출해야 하는 서류는 무엇인가요?",
                                "QUICK_TAP_DOCS"
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "🏢 출근 장소·입장 방법",
                                "첫 출근 장소와 입장 방법이 어떻게 되나요?",
                                "QUICK_TAP_LOCATION"
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "🕘 출근 시간",
                                "출근 시간이 어떻게 되나요?",
                                "QUICK_TAP_WORK_HOUR"
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "📍 첫날 일정 확인",
                                "첫날 전체 일정이 어떻게 진행되나요?",
                                "QUICK_TAP_FIRST_DAY_SCHEDULE"
                        )
                );
    }

    @Test
    void excludesPreOnlyScheduleTargetFromRandomQuickQuestions() {
        QuickQuestionCatalog catalog = new QuickQuestionCatalog();

        assertThat(catalog.getRandomQuickQuestions(100))
                .extracting(QuickQuestionResponse::getEventTarget)
                .doesNotContain("QUICK_TAP_FIRST_DAY_SCHEDULE");
    }

    @Test
    void resolvesPreOnlyScheduleTargetForClickLog() {
        QuickQuestionCatalog catalog = new QuickQuestionCatalog();

        assertThat(catalog.resolveEventTarget("QUICK_TAP_FIRST_DAY_SCHEDULE")).isPresent();
    }
}
