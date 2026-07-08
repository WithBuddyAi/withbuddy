package com.withbuddy.buddy.chat.service;

import com.withbuddy.buddy.chat.dto.response.QuickQuestionResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuickQuestionCatalogTest {

    @Test
    void returnsPreQuickQuestionsInFixedOrder() {
        QuickQuestionCatalog catalog = new QuickQuestionCatalog();

        assertThat(catalog.getPreQuickQuestions())
                .extracting(QuickQuestionResponse::getEventTarget)
                .containsExactly(
                        "QUICK_TAP_DOCS",
                        "QUICK_TAP_LOCATION",
                        "QUICK_TAP_WORK_HOUR",
                        "QUICK_TAP_FIRST_DAY_SCHEDULE"
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
