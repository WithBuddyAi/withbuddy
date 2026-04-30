INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 7일 전~4일 전',
       '입사까지 딱 {N}일 남았네요 🎉 설레는 마음, 저도 느껴져요.\n첫날 당황하지 않도록 미리 알아두면 좋은 것들이에요.',
       -7
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = -7
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 3일~1일 전',
       '드디어 입사 {N}일전이에요 😊 입사 첫날, 이것만 알고 가도 훨씬 편할 거예요.',
       -3
    WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = -3
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 당일',
       '안녕하세요! 저는 위드버디예요 🙂 입사 첫날, 설레기도 하고 낯설기도 하죠?\n{회사명}에서 궁금한 게 생기면 언제든 물어보세요. 사소한 것도 괜찮아요.',
       0
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = 0
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 2일차',
       '이틀째, 어떠세요? 😊 아직 낯선 게 많겠지만, 조금씩 익숙해지고 있죠?',
       1
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = 1
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 3일차',
       '3일째, {회사명}에 조금 익숙해졌나요? 🌱 이쯤 되면 이런 게 궁금해지더라고요.',
       2
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = 2
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 5일차',
       '첫 주가 거의 다 됐어요 🙂 이번 주말 전에 알아두면 좋을 것들이에요.',
       4
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = 4
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 7일차',
       '첫 주를 마쳤어요, 수고했어요 👏 이번 주엔 업무 흐름이 조금씩 보이기 시작할 거예요.',
       6
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = 6
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 10일차',
       '열흘째! 이제 흐름이 좀 보이죠? 💪 슬슬 이런 것들도 궁금해질 거예요.',
       9
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = 9
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 14일차',
       '벌써 2주예요! 많이 적응됐죠? 😄 이제 슬슬 이런 것들도 챙겨봐요.',
       13
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = 13
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 21일차',
       '3주 달성! 이제 진짜 팀원 같은 느낌이죠? 🙌 한 달이 얼마 안 남았어요.',
       20
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = 20
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 30일차',
       '한 달을 해냈어요! 정말 잘하고 있어요 🎊 한 달간의 수습 기간이 지났어요. 궁금한 거 미리 챙겨봐요.',
       29
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = 29
);
