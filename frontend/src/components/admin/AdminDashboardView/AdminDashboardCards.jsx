// 카드별 설정 (API 응답 필드 매핑)
const CARD_CONFIG = [
  {
    id: "usage",
    title: "위드버디 활용률",
    getDotColor: (company) => {
      const rate = company.rag?.ragExperienceRate;
      return rate >= 70 ? "bg-[#0CA678]" : "bg-[#F03E3E]";
    },
    getValue: (company) => {
      const rate = company.rag?.ragExperienceRate;
      return rate != null ? `${rate}%` : "-";
    },
    getStatus: (company) => {
      const rate = company.rag?.ragExperienceRate;
      if (rate == null) return "";
      return rate >= 70 ? "목표 달성" : "활용률을 높여보세요";
    },
    getStatusColor: (company) => {
      const rate = company.rag?.ragExperienceRate;
      return rate >= 70 ? "text-[#099268]" : "text-[#FF6B6B]";
    },
  },
  {
    id: "gap",
    title: "문서 보강 필요율",
    getDotColor: (company) => {
      const rate = company.gap?.documentGapRate;
      return rate >= 70 ? "bg-[#0CA678]" : "bg-[#F03E3E]";
    },
    getValue: (company) => {
      const rate = company.gap?.documentGapRate;
      return rate != null ? `${rate}%` : "-";
    },
    getStatus: (company) => {
      const rate = company.gap?.documentGapRate;
      if (rate == null) return "";
      return rate > 0 ? "문서 보강이 필요해요" : "보강할 문서가 없어요";
    },
    getStatusColor: (company) => {
      const rate = company.gap?.documentGapRate;
      return rate > 0 ? "text-[#FF6B6B]" : "text-[#495057]";
    },
  },
  {
    id: "unstarted",
    title: "미시작 신입 수",
    getDotColor: (company) => {
      const rate = company.unstarted?.unstartedUsers;
      return rate >= 70 ? "bg-[#0CA678]" : "bg-[#F03E3E]";
    },
    getValue: (company) => {
      const count = company.unstarted?.unstartedUsers;
      return count != null ? `${count}명` : "-";
    },
    getStatus: (company) => {
      const count = company.unstarted?.unstartedUsers;
      if (count == null) return "";
      return count > 0
        ? "아직 시작하지 않은 신입이 있어요"
        : "모든 신입이 시작했어요";
    },
    getStatusColor: (company) => {
      const count = company.unstarted?.unstartedUsers;
      return count > 0 ? "text-[#E03131CC]" : "text-[#495057]";
    },
  },
];

function AdminDashboardCards({ company, isLoading, error, onRetry }) {
  // 로딩 상태
  if (isLoading) {
    return (
      <div className="flex flex-col md:flex-row gap-[16px] md:gap-[24px] lg:gap-[36px] my-[32px] md:my-[40px] lg:my-[56px]">
        {[1, 2, 3].map((i) => (
          <div
            key={i}
            className="flex-1 px-[20px] py-[16px] md:px-[24px] md:py-[20px] border border-[#E9ECEF] rounded-[12px] bg-[#F7FBFF] animate-pulse"
          >
            <div className="h-[14px] w-[100px] bg-[#DEE2E6] rounded mb-[16px]" />
            <div className="h-[32px] w-[80px] bg-[#DEE2E6] rounded mb-[8px]" />
            <div className="h-[13px] w-[120px] bg-[#DEE2E6] rounded" />
          </div>
        ))}
      </div>
    );
  }

  // 에러 상태
  if (error) {
    return (
      <div className="my-[32px] md:my-[40px] lg:my-[56px] flex flex-col items-center gap-[16px] py-[40px]">
        <p className="text-[#868E96] text-[14px]">
          데이터를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
        </p>
        <button
          onClick={onRetry}
          className="flex items-center gap-[6px] text-[13px] rounded-[9999px] py-[8px] px-[16px] bg-[#EAF6FF] text-[#204867] hover:bg-[#D2E2F6]"
        >
          다시 시도
        </button>
      </div>
    );
  }

  return (
    <div className="flex flex-col md:flex-row gap-[12px] md:gap-[24px] lg:gap-[36px] my-[14px] md:my-[28px] lg:my-[56px]">
      {CARD_CONFIG.map((card) => (
        <div
          key={card.id}
          className="flex-1 lg:px-[20px] py-[16px] px-[18px] md:py-[20px] border border-[#E9ECEF] rounded-[12px] bg-[#F7FBFF] shadow-[0_1px_3px_rgba(0,0,0,0.2)]"
        >
          {/* 점 + 제목 */}
          <div className="flex items-center gap-[8px] mb-[8px] md:mb-[12px]">
            <span
              className={`w-[8px] h-[8px] rounded-full shrink-0 ${card.getDotColor(company)}`}
            />
            <p className="text-[#495057] text-[12px] md:text-[13px] lg:text-[14px]">
              {card.title}
            </p>
          </div>

          {/* 수치 */}
          <p className="text-[#336B97] text-[24px] md:text-[28px] lg:text-[32px] font-semibold mb-[2px] md:mb-[4px]">
            {card.getValue(company)}
          </p>

          {/* 상태 텍스트 */}
          <p
            className={`font-medium text-[12px] md:text-[13px] lg:text-[14px] ${card.getStatusColor(company)}`}
          >
            {card.getStatus(company)}
          </p>
        </div>
      ))}
    </div>
  );
}

export default AdminDashboardCards;
