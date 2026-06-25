import bot from "../../../assets/Bot_icon.svg";

function AdminDashboardQuestions({ patterns, aiSummary, isLoading, error, onRetry }) {
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
            데이터를 불러오지 못했어요.
          </p>
          <button
            onClick={onRetry}
            className="flex items-center gap-[6px] text-[13px] rounded-[9999px] py-[8px] px-[16px] bg-[#EAF6FF] text-[#204867] hover:bg-[#D2E2F6]"
          >
            다시 시도
          </button>
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

                {/* 모바일: truncate 없이 전체 표시, 데스크탑: 고정 너비 + truncate */}
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

      {/* AI 분석 — API 응답(aiSummary) 기반 렌더링 */}
      {aiSummary && aiSummary.status === "READY" && (
        <div className="border border-[#E9ECEF] rounded-[12px] p-[16px] md:p-[24px] bg-[#F7FAFBCC]">
          {/* 섹션 레이블 */}
          <h3 className="flex gap-[5px] items-center text-[14px] md:text-[16px] font-medium text-[#000000] mb-[10px] md:mb-[16px]">
            <img src={bot} alt="Bot" className="w-[18px] md:w-[20px]" /> AI 분석
          </h3>

          {/* 요약 텍스트 (방식 A) */}
          <p className="text-[12px] md:text-[14px] lg:text-[16px] text-[#000000] leading-[1.7] mb-[14px] md:mb-[20px]">
            {aiSummary.summary}
          </p>

          {/* 보강 필요 영역 (방식 B) */}
          {aiSummary.actions?.length > 0 && (
            <div>
              <p className="text-[12px] md:text-[14px] font-semibold text-[#204867] mb-[8px] md:mb-[12px]">
                보강이 필요한 영역
              </p>
              <ul className="flex flex-col gap-[6px] md:gap-[8px]">
                {aiSummary.actions.map((action, index) => (
                  <li
                    key={index}
                    className="text-[12px] md:text-[14px] lg:text-[15px] text-[#000000]"
                  >
                    <span
              className="w-[8px] h-[8px] rounded-full shrink-0"
            />

                    <span className="font-semibold">
                      {action.part}
                    </span>
                    {"  —  "}
                    {action.items}
                  </li>
                ))}
              </ul>
            </div>
          )}

          {/* 민감 질문 안내 (조건부 노출) */}
          {aiSummary.hasSensitive && (
            <p className="text-[11px] md:text-[13px] text-[#868E96] mt-[14px] md:mt-[20px]">
              ⚠️ 일부 질문은 문서 보강보다 HR/법무 담당자 직접 상담이 필요한
              내용입니다.
            </p>
          )}
        </div>
      )}

      {/* AI 분석 에러 상태 */}
      {aiSummary && aiSummary.status !== "READY" && aiSummary.errorMessage && (
        <div className="border border-[#E9ECEF] rounded-[12px] p-[16px] md:p-[24px] bg-[#F7FAFBCC]">
          <h3 className="flex gap-[5px] items-center text-[14px] md:text-[16px] font-medium text-[#000000] mb-[10px] md:mb-[16px]">
            <img src={bot} alt="Bot" className="w-[18px] md:w-[20px]" /> AI 분석
          </h3>
          <p className="text-[12px] md:text-[14px] text-[#868E96]">
            {aiSummary.errorMessage}
          </p>
        </div>
      )}
    </div>
  );
}

export default AdminDashboardQuestions;
