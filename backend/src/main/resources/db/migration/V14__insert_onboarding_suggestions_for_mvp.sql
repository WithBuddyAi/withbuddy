INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 7일 전',
       '{회사명} 첫 출근이 일주일 앞으로 다가왔어요. 출근 장소, 출근 시간, 복장처럼 첫날 바로 필요한 정보부터 미리 확인해 두면 좋아요.',
       -7
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = -7
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 하루 전',
       '내일이 {회사명} 첫 출근일이에요. 첫날 누구를 찾아가야 하는지, 출입카드는 어떻게 받는지, 제출할 서류는 무엇인지 미리 점검해 보세요.',
       -1
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = -1
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 당일',
       '안녕하세요! 저는 위드버디예요 🙂 입사 첫날, 설레기도 하고 낯설기도 하죠? {회사명}에서 궁금한 게 생기면 언제든 물어보세요. 사소한 것도 괜찮아요.',
       0
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = 0
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 2일 차',
       '{회사명} 업무 환경이 조금씩 익숙해질 시점이에요. 프린터, 회의실 예약, 점심 식대처럼 자주 쓰는 생활형 정보를 확인해 두면 도움이 됩니다.',
       2
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = 2
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 3일 차',
       '이제 기본 적응이 시작되는 시점이에요. {회사명}의 복지, 경비 처리, 보안 규칙처럼 실무에서 자주 마주치는 기준을 정리해 보세요.',
       3
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = 3
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 5일 차',
       '첫 주를 보내면서 근태와 휴가 규정이 궁금해질 수 있어요. {회사명}의 지각, 조퇴, 반차, 병가 처리 기준을 미리 확인해 두세요.',
       5
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = 5
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 7일 차',
       '업무 흐름이 본격적으로 시작되는 시점이에요. {회사명}에서 결재는 어떻게 올리는지, 시스템 권한은 어디서 신청하는지 확인해 보세요.',
       7
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = 7
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 10일 차',
       '조금 더 자주 쓰게 되는 운영 정보들이 생기는 시점이에요. {회사명}의 복지 사용 시점, 영수증 처리, 업무 보고 방식을 챙겨 두면 좋아요.',
       10
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = 10
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 14일 차',
       '온보딩의 중간 지점을 지나고 있어요. {회사명} 복지 신청 방법, 급여명세서 확인 경로, 수습 평가 기준을 한 번 정리해 보세요.',
       14
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = 14
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 21일 차',
       '이제 제도 활용 범위가 넓어질 시점이에요. {회사명}에서 연차 신청, 건강검진, 교육·자기계발 지원을 어떻게 쓰는지 확인해 보세요.',
       21
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = 21
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 30일 차',
       '한 달 차에 접어들었어요. {회사명}에서 정규직 전환 절차와 수습 평가, 전환 후 평가 방식을 점검해 두면 다음 단계 준비에 도움이 됩니다.',
       30
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions WHERE day_offset = 30
);
