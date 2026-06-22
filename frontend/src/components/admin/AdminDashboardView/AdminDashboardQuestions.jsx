import bot from "../../../assets/Bot_icon.svg";

function AdminDashboardQuestions({ patterns, isLoading, error, onRetry }) {
  // 로딩 상태
  if (isLoading) {
    return (
      <div className="flex flex-col gap-[24px] md:gap-[32px]">
        <div>
          <div className="flex items-center justify-between mb-[20px] md:mb-[24px]">
            <div className="h-[18px] w-[200px] bg-[#DEE2E6] rounded animate-pulse" />
            <div className="h-[14px] w-[50px] bg-[#DEE2E6] rounded animate-pulse" />
          </div>
          {[1, 2, 3, 4, 5].map((i) => (
            <div key={i} className="mb-[16px] md:mb-[20px] animate-pulse">
              <div className="flex items-center gap-[8px] mb-[8px] md:mb-0">
                <div className="h-[16px] w-[20px] bg-[#DEE2E6] rounded" />
                <div className="h-[16px] w-[180px] md:w-[200px] bg-[#DEE2E6] rounded" />
              </div>
              <div className="flex items-center gap-[8px] ml-[28px] md:hidden">
                <div className="flex-1 h-[8px] bg-[#E9ECEF] rounded-full" />
                <div className="h-[14px] w-[36px] bg-[#DEE2E6] rounded" />
              </div>
            </div>
          ))}
        </div>

        {/* AI 분석 로딩 */}
        <div className="border border-[#E9ECEF] rounded-[12px] p-[16px] md:p-[24px] bg-[#F7FAFBCC]">
          <div className="flex items-center gap-[8px] text-[13px] md:text-[14px] text-[#868E96]">
            <div className="w-[16px] h-[16px] border-2 border-[#868E96] border-t-transparent rounded-full animate-spin" />
            AI가 질문을 분석하고 있어요...
          </div>
        </div>
      </div>
    );
  }

  // 에러 상태
  if (error) {
    return (
      <div className="flex flex-col gap-[20px] md:gap-[32px] bg-[#FFFFFFCC] border px-[16px] py-[20px] md:px-[32px] md:py-[24px] rounded-[16px] shadow-[0_1px_3px_rgba(0,0,0,0.2)]">
        {/* TOP5 에러 */}
        <div className="flex flex-col items-center gap-[16px] py-[40px]">
          <p className="text-[#868E96] text-[14px]">
            데이터를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
          </p>
        </div>

        {/* AI 분석 에러 */}
        <div className="border border-[#E9ECEF] rounded-[12px] p-[16px] md:p-[24px] bg-[#F7FAFBCC]">
          <h3 className="flex gap-[5px] items-center text-[14px] md:text-[16px] font-medium text-[#000000] mb-[10px] md:mb-[16px]">
            <img src={bot} alt="Bot" className="w-[18px] md:w-[20px]" /> AI 분석
          </h3>
          <p className="text-[12px] md:text-[14px] text-[#868E96]">
            분석을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
          </p>
        </div>
      </div>
    );
  }

  // 빈 상태
  if (!patterns || patterns.length === 0) {
    return (
      <div className="flex flex-col gap-[20px] md:gap-[32px] bg-[#FFFFFFCC] border px-[16px] py-[20px] md:px-[32px] md:py-[24px] rounded-[16px] shadow-[0_1px_3px_rgba(0,0,0,0.2)]">
        <div>
          <div className="flex items-center justify-between mb-[16px] md:mb-[24px]">
            <h2 className="text-[14px] md:text-[16px] font-medium text-[#000000]">
              문서 보강 후보 질문 TOP5
            </h2>
            <span className="text-[12px] md:text-[14px] text-[#868E96]">
              최근 7일
            </span>
          </div>
          <div className="text-center py-[40px] text-[#868E96] text-[14px] leading-[1.8]">
            최근 7일간 미답변 질문이 없어요.
            <br />
            문서가 신입 질문을 잘 커버하고 있어요 👍
          </div>
        </div>
      </div>
    );
  }

  // 막대 그래프 최대값
  const maxCount = patterns[0]?.totalCount || 1;
  const areas = extractAreas(patterns);
  // TODO: 백엔드 AI 분석 API에서 hasSensitive 플래그 받으면 교체
  const hasSensitive = false;

  return (
    <div className="flex flex-col gap-[20px] md:gap-[32px] bg-[#FFFFFFCC] border px-[16px] py-[20px] md:px-[32px] md:py-[24px] rounded-[16px] shadow-[0_1px_3px_rgba(0,0,0,0.2)]">
      {/* 문서 보강 후보 질문 TOP5 */}
      <div>
        <div className="flex items-center justify-between mb-[16px] md:mb-[24px]">
          <h2 className="text-[14px] md:text-[16px] font-medium text-[#000000]">
            문서 보강 후보 질문 TOP5
          </h2>
          <span className="text-[12px] md:text-[14px] text-[#868E96]">
            최근 7일
          </span>
        </div>

        <div className="flex flex-col gap-[12px] md:gap-[20px]">
          {patterns.map((item, index) => (
            <div key={`${item.questionContent}-${index}`}>
              {/* 순위 + 질문 텍스트 */}
              <div className="flex items-center gap-[8px] md:gap-[16px] mb-[6px] md:mb-0">
                <span className="bg-[#D0EBFF66] rounded-[4px] text-[12px] md:text-[14px] text-[#4791CA] w-[22px] text-center shrink-0">
                  {index + 1}
                </span>

                <p className="text-[12px] md:text-[14px] text-[#000000] md:w-[240px] lg:w-[300px] md:shrink-0 md:truncate">
                  {item.questionContent}
                </p>

                {/* 데스크탑: 막대 + 건수 같은 줄 */}
                <div className="hidden md:flex flex-1 items-center gap-[16px]">
                  <div className="flex-1 h-[10px] bg-[#336B971A] rounded-full overflow-hidden">
                    <div
                      className="h-full bg-[#4791CA] rounded-full transition-all duration-500"
                      style={{
                        width: `${(item.totalCount / maxCount) * 100}%`,
                      }}
                    />
                  </div>
                  <span className="text-[14px] text-[#336B97] w-[38px] text-right shrink-0">
                    {item.totalCount}건
                  </span>
                </div>
              </div>

              {/* 모바일: 막대 + 건수 아래줄 */}
              <div className="flex items-center gap-[8px] ml-[30px] md:hidden">
                <div className="flex-1 h-[8px] bg-[#336B971A] rounded-full overflow-hidden">
                  <div
                    className="h-full bg-[#4791CA] rounded-full transition-all duration-500"
                    style={{
                      width: `${(item.totalCount / maxCount) * 100}%`,
                    }}
                  />
                </div>
                <span className="text-[12px] text-[#336B97] w-[36px] text-right shrink-0">
                  {item.totalCount}건
                </span>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* AI 분석 */}
      {/* TODO: 백엔드 AI 분석 API가 생기면 API 응답으로 교체 */}
      <div className="border border-[#E9ECEF] rounded-[12px] p-[16px] md:p-[24px] bg-[#F7FAFBCC]">
        {/* 섹션 레이블 */}
        <h3 className="flex gap-[5px] items-center text-[14px] md:text-[16px] font-medium text-[#000000] mb-[10px] md:mb-[16px]">
          <img src={bot} alt="Bot" className="w-[18px] md:w-[20px]" /> AI 분석
        </h3>

        {/* 요약 텍스트 */}
        <p className="text-[12px] md:text-[14px] lg:text-[16px] text-[#000000] leading-[1.7] mb-[14px] md:mb-[20px]">
          {generateAISummary(patterns)}
        </p>

        {/* 액션 리스트 */}
        {areas.length > 0 && (
          <div>
            <p className="text-[12px] md:text-[14px] font-semibold text-[#204867] mb-[8px] md:mb-[12px]">
              보강이 필요한 영역
            </p>
            <ul className="flex flex-col gap-[6px] md:gap-[8px]">
              {areas.map((area) => (
                <li
                  key={area.category}
                  className="text-[12px] md:text-[14px] lg:text-[15px] text-[#495057]"
                >
                  <span className="font-semibold text-[#000000]">
                    {area.category}
                  </span>
                  {"  —  "}
                  {area.detail}
                </li>
              ))}
            </ul>
          </div>
        )}

        {/* 민감 질문 안내 (조건부 노출) */}
        {/* TODO: 백엔드 AI 분석 API에서 hasSensitive 플래그 받으면 연동 */}
        {hasSensitive && (
          <p className="text-[11px] md:text-[13px] text-[#868E96] mt-[14px] md:mt-[20px]">
            ⚠️ 일부 질문은 문서 보강보다 HR/법무 담당자 직접 상담이 필요한
            내용입니다.
          </p>
        )}
      </div>
    </div>
  );
}

// AI 분석 요약 텍스트 생성 (방식 A: 맥락 요약)
// TODO: 백엔드 AI 분석 API가 생기면 이 함수를 API 응답으로 교체
function generateAISummary(patterns) {
  if (!patterns || patterns.length === 0) {
    return "분석할 미답변 질문이 없습니다.";
  }

  // 질문 1건일 때 단수 처리
  if (patterns.length === 1) {
    const q = patterns[0].questionContent;
    return `${q.replace("?", "")} 관련 질문이 있었습니다. 해당 내용을 온보딩 문서에 추가하면 같은 질문을 줄일 수 있어요.`;
  }

  // 카테고리별 그룹핑 후 요약 생성
  const grouped = groupByCategory(patterns);
  const summaryParts = [];

  for (const [category, questions] of Object.entries(grouped)) {
    const keywords = questions
      .map((q) => extractKeyword(q))
      .filter(Boolean)
      .slice(0, 2);
    if (keywords.length > 0) {
      summaryParts.push(`${category}(${keywords.join(", ")})`);
    }
  }

  // 그룹핑 실패 시 질문 직접 나열
  if (summaryParts.length === 0) {
    const topQuestions = patterns
      .slice(0, 3)
      .map((p) => p.questionContent)
      .join(", ");
    return `"${topQuestions}" 등의 질문이 반복되고 있습니다. 해당 내용을 온보딩 문서에 추가하면 같은 질문을 줄일 수 있어요.`;
  }

  const joined =
    summaryParts.length <= 2
      ? summaryParts.join("과 ")
      : summaryParts.slice(0, -1).join(", ") +
        ", " +
        summaryParts[summaryParts.length - 1];

  return `${joined}에 대한 질문이 반복되고 있습니다. 해당 내용을 온보딩 문서에 추가하면 같은 질문을 줄일 수 있어요.`;
}

// 질문에서 핵심 키워드 추출 (간단한 규칙 기반)
function extractKeyword(questionContent) {
  const removePatterns = [
    /은\s*어떻게/,
    /는\s*어떻게/,
    /어떻게\s*(되|하|신청|받)/,
    /있나요\??/,
    /인가요\??/,
    /하나요\??/,
    /되나요\??/,
    /받을\s*수/,
    /쓸\s*수/,
    /운영해요\??/,
    /신청/,
    /\?/,
  ];

  let keyword = questionContent;
  removePatterns.forEach((pattern) => {
    keyword = keyword.replace(pattern, "");
  });

  return keyword.trim();
}

// 질문을 카테고리별로 그룹핑
function groupByCategory(patterns) {
  const categoryMap = {
    "보상 조건": ["스톡옵션", "사이닝", "보너스", "급여", "연봉"],
    "근무 제도": ["4일제", "리프레시", "휴가", "연차", "재택", "출퇴근"],
    "사내 복리후생": ["동호회", "복지", "식대", "건강검진", "경조"],
    행정: ["비품", "장비", "회의실", "경비", "출장"],
    IT: ["이메일", "계정", "장비", "보안", "VPN", "프린터"],
  };

  const grouped = {};

  patterns.forEach((p) => {
    for (const [category, keywords] of Object.entries(categoryMap)) {
      if (keywords.some((kw) => p.questionContent.includes(kw))) {
        if (!grouped[category]) grouped[category] = [];
        grouped[category].push(p.questionContent);
        return;
      }
    }
  });

  return grouped;
}

// 패턴 데이터에서 보강 필요 영역 추출 (방식 B: 액션 리스트)
// TODO: 백엔드 AI 분석 API가 생기면 이 함수를 API 응답으로 교체
function extractAreas(patterns) {
  if (!patterns || patterns.length === 0) return [];

  // 파트 분류 기준 (기획 문서 4-4 기준)
  const categoryMap = {
    "HR · 인사": [
      "스톡옵션",
      "사이닝",
      "보너스",
      "급여",
      "연봉",
      "경조사",
      "근로계약",
      "수습",
      "정규직",
    ],
    복지: [
      "동호회",
      "복지",
      "식대",
      "건강검진",
      "경조금",
      "자기계발",
      "복지카드",
    ],
    행정: ["비품", "장비", "회의실", "경비", "출장"],
    IT: ["이메일", "계정", "보안", "VPN", "프린터", "업무 도구"],
    "근무 제도": ["4일제", "리프레시", "휴가", "연차", "재택", "출퇴근"],
  };

  const matched = {};

  patterns.forEach((p) => {
    for (const [category, keywords] of Object.entries(categoryMap)) {
      if (keywords.some((kw) => p.questionContent.includes(kw))) {
        if (!matched[category]) matched[category] = [];
        matched[category].push(extractKeyword(p.questionContent));
        break;
      }
    }
  });

  // 최대 3개 파트로 제한
  return Object.entries(matched)
    .slice(0, 3)
    .map(([category, keywords]) => ({
      category,
      detail: keywords.join(", "),
    }));
}

export default AdminDashboardQuestions;
