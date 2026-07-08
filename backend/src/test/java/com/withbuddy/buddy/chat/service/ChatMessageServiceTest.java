package com.withbuddy.buddy.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.withbuddy.account.auth.repository.UserRepository;
import com.withbuddy.account.company.entity.Company;
import com.withbuddy.account.user.entity.User;
import com.withbuddy.account.user.entity.UserAccountStatus;
import com.withbuddy.account.user.entity.UserRole;
import com.withbuddy.buddy.chat.dto.request.ChatMessageRequest;
import com.withbuddy.buddy.chat.dto.response.QuickQuestionResponse;
import com.withbuddy.buddy.chat.entity.ChatMessage;
import com.withbuddy.buddy.chat.entity.MessageType;
import com.withbuddy.buddy.chat.entity.SenderType;
import com.withbuddy.buddy.chat.entity.UnansweredQuestionLog;
import com.withbuddy.buddy.chat.repository.ChatMessageDocumentRepository;
import com.withbuddy.buddy.chat.repository.ChatMessageRepository;
import com.withbuddy.buddy.chat.repository.UnansweredQuestionLogRepository;
import com.withbuddy.buddy.onboarding.repository.OnboardingSuggestionRepository;
import com.withbuddy.global.security.JwtAuthenticationPrincipal;
import com.withbuddy.infrastructure.ai.client.AiStreamClient;
import com.withbuddy.infrastructure.ai.dto.AiAnswerServerResponse;
import com.withbuddy.infrastructure.redis.RedisCacheService;
import com.withbuddy.storage.repository.DocumentFileRepository;
import com.withbuddy.storage.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Clock FIXED_CLOCK = Clock.fixed(
            LocalDate.of(2026, 7, 10).atStartOfDay(KST).toInstant(),
            KST
    );

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatMessageDocumentRepository chatMessageDocumentRepository;
    @Mock
    private UnansweredQuestionLogRepository unansweredQuestionLogRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentFileRepository documentFileRepository;
    @Mock
    private AiStreamClient aiStreamClient;
    @Mock
    private RedisCacheService redisCacheService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private Executor aiCallExecutor;
    @Mock
    private QuickQuestionCatalog quickQuestionCatalog;
    @Mock
    private OnboardingSuggestionRepository onboardingSuggestionRepository;
    @Mock
    private UserRepository userRepository;

    private ChatMessageService chatMessageService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        chatMessageService = new ChatMessageService(
                chatMessageRepository,
                chatMessageDocumentRepository,
                unansweredQuestionLogRepository,
                documentRepository,
                documentFileRepository,
                aiStreamClient,
                redisCacheService,
                objectMapper,
                transactionTemplate,
                aiCallExecutor,
                quickQuestionCatalog,
                onboardingSuggestionRepository,
                userRepository,
                FIXED_CLOCK
        );
    }

    @Test
    void savesSuggestionMessageWhenNoExistingRow() {
        Long userId = 1L;
        Long suggestionId = 7L;
        ChatMessage saved = ChatMessage.createSuggestionMessage(userId, suggestionId, "hello");

        when(chatMessageRepository.findTopByUserIdAndSuggestionIdAndMessageTypeOrderByCreatedAtDesc(
                userId, suggestionId, MessageType.suggestion
        )).thenReturn(Optional.empty());
        when(chatMessageRepository.save(org.mockito.ArgumentMatchers.any(ChatMessage.class))).thenReturn(saved);

        ChatMessage result = chatMessageService.saveSuggestionMessageIfNotExists(userId, suggestionId, "hello");

        assertThat(result).isSameAs(saved);
        verify(chatMessageRepository, times(1)).save(org.mockito.ArgumentMatchers.any(ChatMessage.class));
    }

    @Test
    void returnsExistingRowWhenUniqueConstraintRaceOccurs() {
        Long userId = 1L;
        Long suggestionId = 7L;
        ChatMessage existing = ChatMessage.createSuggestionMessage(userId, suggestionId, "hello");

        when(chatMessageRepository.findTopByUserIdAndSuggestionIdAndMessageTypeOrderByCreatedAtDesc(
                userId, suggestionId, MessageType.suggestion
        )).thenReturn(Optional.empty(), Optional.of(existing));
        when(chatMessageRepository.save(org.mockito.ArgumentMatchers.any(ChatMessage.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        ChatMessage result = chatMessageService.saveSuggestionMessageIfNotExists(userId, suggestionId, "hello");

        assertThat(result).isSameAs(existing);
        verify(chatMessageRepository, times(2))
                .findTopByUserIdAndSuggestionIdAndMessageTypeOrderByCreatedAtDesc(
                        userId, suggestionId, MessageType.suggestion
                );
    }

    @Test
    void resolvesAnswerToMessageIdOnlyForNoResult() {
        Long result = chatMessageService.resolveAnswerToMessageId(MessageType.no_result, 201L);

        assertThat(result).isEqualTo(201L);
    }

    @Test
    void doesNotResolveAnswerToMessageIdForOtherMessageTypes() {
        Long result = chatMessageService.resolveAnswerToMessageId(MessageType.rag_answer, 201L);

        assertThat(result).isNull();
    }

    @Test
    void createsUnansweredQuestionLogForNoResult() {
        boolean result = chatMessageService.shouldCreateUnansweredQuestionLog(MessageType.no_result);

        assertThat(result).isTrue();
    }

    @Test
    void createsUnansweredQuestionLogForOutOfScope() {
        boolean result = chatMessageService.shouldCreateUnansweredQuestionLog(MessageType.out_of_scope);

        assertThat(result).isTrue();
    }

    @Test
    void createsUnansweredQuestionLogForOutOfScopePre() {
        boolean result = chatMessageService.shouldCreateUnansweredQuestionLog(MessageType.out_of_scope_pre);

        assertThat(result).isTrue();
    }

    @Test
    void createsUnansweredQuestionLogForSensitive() {
        boolean result = chatMessageService.shouldCreateUnansweredQuestionLog(MessageType.sensitive);

        assertThat(result).isTrue();
    }

    @Test
    void doesNotCreateUnansweredQuestionLogForRagAnswer() {
        boolean result = chatMessageService.shouldCreateUnansweredQuestionLog(MessageType.rag_answer);

        assertThat(result).isFalse();
    }

    @Test
    void doesNotCreateUnansweredQuestionLogForClarifying() {
        boolean result = chatMessageService.shouldCreateUnansweredQuestionLog(MessageType.clarifying);

        assertThat(result).isFalse();
    }

    @Test
    void savesNoResultLogWithoutQuestionEmbedding() {
        Long userId = 1L;
        Long questionMessageId = 201L;
        ChatMessage savedAnswer = new ChatMessage(
                userId,
                null,
                SenderType.BOT,
                MessageType.no_result,
                "답변할 수 없습니다.",
                null,
                questionMessageId,
                123L
        );
        ReflectionTestUtils.setField(savedAnswer, "id", 302L);

        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(savedAnswer);
        when(unansweredQuestionLogRepository.save(any(UnansweredQuestionLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChatMessage result = chatMessageService.saveAnswerMessage(
                userId,
                " ACME ",
                "복지 포인트는 어디서 확인해?",
                questionMessageId,
                questionMessageId,
                MessageType.no_result,
                "답변할 수 없습니다.",
                List.of(),
                null,
                123L
        );

        ArgumentCaptor<UnansweredQuestionLog> logCaptor = ArgumentCaptor.forClass(UnansweredQuestionLog.class);
        verify(unansweredQuestionLogRepository).save(logCaptor.capture());

        UnansweredQuestionLog savedLog = logCaptor.getValue();
        assertThat(result).isSameAs(savedAnswer);
        assertThat(savedLog.getUserId()).isEqualTo(userId);
        assertThat(savedLog.getCompanyCode()).isEqualTo("ACME");
        assertThat(savedLog.getQuestionMessageId()).isEqualTo(questionMessageId);
        assertThat(savedLog.getAnswerMessageId()).isEqualTo(302L);
        assertThat(savedLog.getQuestionContent()).isEqualTo("복지 포인트는 어디서 확인해?");
        assertThat(savedLog.getAnswerType()).isEqualTo(MessageType.no_result);
        assertThat(savedLog.getLatencyMs()).isEqualTo(123L);
    }

    @Test
    void returnsFixedPreQuickQuestionsForPreUser() {
        User preUser = user(UserAccountStatus.PRE, LocalDate.of(2026, 7, 14));
        List<QuickQuestionResponse> preQuickQuestions = List.of(
                new QuickQuestionResponse("1", "1", "QUICK_TAP_LOCATION"),
                new QuickQuestionResponse("2", "2", "QUICK_TAP_WORK_HOUR"),
                new QuickQuestionResponse("3", "3", "QUICK_TAP_DRESSCODE"),
                new QuickQuestionResponse("4", "4", "QUICK_TAP_FIRST_DAY")
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(preUser));
        when(quickQuestionCatalog.getPreQuickQuestions()).thenReturn(preQuickQuestions);

        assertThat(chatMessageService.getQuickQuestions(1L).get("quickQuestions"))
                .extracting(QuickQuestionResponse::getEventTarget)
                .containsExactly(
                        "QUICK_TAP_LOCATION",
                        "QUICK_TAP_WORK_HOUR",
                        "QUICK_TAP_DRESSCODE",
                        "QUICK_TAP_FIRST_DAY"
                );
        verify(quickQuestionCatalog).getPreQuickQuestions();
    }

    @Test
    void returnsRandomQuickQuestionsForActiveUser() {
        User activeUser = user(UserAccountStatus.ACTIVE, LocalDate.of(2026, 7, 10));
        List<QuickQuestionResponse> randomQuickQuestions = List.of(
                new QuickQuestionResponse("1", "1", "QUICK_TAP_LOCATION")
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(quickQuestionCatalog.getRandomQuickQuestions(5)).thenReturn(randomQuickQuestions);

        assertThat(chatMessageService.getQuickQuestions(1L).get("quickQuestions"))
                .extracting(QuickQuestionResponse::getEventTarget)
                .containsExactly("QUICK_TAP_LOCATION");
        verify(quickQuestionCatalog).getRandomQuickQuestions(5);
    }

    @Test
    void streamUserMessageUsesResolvedAccountStatusForAiRequest() throws Exception {
        JwtAuthenticationPrincipal principal = new JwtAuthenticationPrincipal(
                1L,
                "E001",
                "tester",
                "WB0003",
                "WithBuddy",
                "2026-07-14"
        );
        ChatMessageRequest request = new ChatMessageRequest();
        ReflectionTestUtils.setField(request, "content", "사전 온보딩 질문");

        User staleInactiveUser = user(UserAccountStatus.INACTIVE, LocalDate.of(2026, 7, 14));
        ChatMessage savedQuestion = new ChatMessage(1L, null, SenderType.USER, MessageType.user_question, "사전 온보딩 질문", null);
        ReflectionTestUtils.setField(savedQuestion, "id", 101L);
        ReflectionTestUtils.setField(savedQuestion, "createdAt", LocalDateTime.of(2026, 7, 10, 9, 0));

        ChatMessage savedAnswer = new ChatMessage(1L, null, SenderType.BOT, MessageType.rag_answer, "답변", null, null, 10L);
        ReflectionTestUtils.setField(savedAnswer, "id", 102L);
        ReflectionTestUtils.setField(savedAnswer, "createdAt", LocalDateTime.of(2026, 7, 10, 9, 0, 1));

        AiAnswerServerResponse aiResponse = new AiAnswerServerResponse();
        ReflectionTestUtils.setField(aiResponse, "questionId", 101L);
        ReflectionTestUtils.setField(aiResponse, "messageType", MessageType.rag_answer);
        ReflectionTestUtils.setField(aiResponse, "content", "답변");
        ReflectionTestUtils.setField(aiResponse, "documents", List.of());
        ReflectionTestUtils.setField(aiResponse, "recommendedContacts", List.of());

        when(userRepository.findById(1L)).thenReturn(Optional.of(staleInactiveUser));
        when(chatMessageRepository.findTopByUserIdAndSenderTypeAndMessageTypeOrderByCreatedAtDesc(
                1L, SenderType.USER, MessageType.user_question
        )).thenReturn(Optional.empty());
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(savedQuestion, savedAnswer);
        org.mockito.Mockito.lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        org.mockito.Mockito.lenient().when(redisCacheService.listRange(any(), any(Long.class), any(Long.class))).thenReturn(List.of());
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null)
        );
        org.mockito.Mockito.doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(aiCallExecutor).execute(any(Runnable.class));
        when(aiStreamClient.streamAnswer(
                org.mockito.ArgumentMatchers.eq(101L),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("tester"),
                org.mockito.ArgumentMatchers.eq("WB0003"),
                org.mockito.ArgumentMatchers.eq("2026-07-14"),
                org.mockito.ArgumentMatchers.eq("PRE"),
                org.mockito.ArgumentMatchers.eq("사전 온보딩 질문"),
                any()
        )).thenReturn(aiResponse);

        chatMessageService.streamUserMessage(principal, request);

        verify(aiStreamClient).streamAnswer(
                org.mockito.ArgumentMatchers.eq(101L),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("tester"),
                org.mockito.ArgumentMatchers.eq("WB0003"),
                org.mockito.ArgumentMatchers.eq("2026-07-14"),
                org.mockito.ArgumentMatchers.eq("PRE"),
                org.mockito.ArgumentMatchers.eq("사전 온보딩 질문"),
                any()
        );
    }

    private User user(UserAccountStatus accountStatus, LocalDate hireDate) {
        Company company = org.mockito.Mockito.mock(Company.class);
        org.mockito.Mockito.lenient().when(company.getProbationPeriod()).thenReturn(90);

        return User.builder()
                .company(company)
                .name("tester")
                .department("-")
                .teamName("-")
                .employeeNumber("E001")
                .hireDate(hireDate)
                .role(UserRole.USER)
                .accountStatus(accountStatus)
                .build();
    }
}
